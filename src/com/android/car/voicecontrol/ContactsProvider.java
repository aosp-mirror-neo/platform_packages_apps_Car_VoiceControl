/**
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.voicecontrol;

import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.Observer;

import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A provider of contact information. Because reading and indexing contacts can take some time,
 * it is best to index them in the background ahead of time. On a production version, this work
 * would be done by a
 */
public class ContactsProvider {
    private static final String TAG = "Mica.Contacts";

    private Observer<List<Contact>> mContactsObserver = this::indexContacts;
    private Map<String, Set<Contact>> mContactsByPhoneticKey = new HashMap<>();

    public ContactsProvider(Context context) {
        Log.d(TAG, "Current user: " + context.getUser());
        InMemoryPhoneBook.init(context);
        InMemoryPhoneBook.get().getContactsLiveData().observeForever(mContactsObserver);
    }

    /**
     * Releases resources used by this provider
     */
    public void destroy() {
        InMemoryPhoneBook.get().getContactsLiveData().removeObserver(mContactsObserver);
        InMemoryPhoneBook.tearDown();
    }

    /**
     * A production voice control application would add contacts to their models for recognition and
     * disambiguation.
     * The code below uses Soundex (https://en.wikipedia.org/wiki/Soundex) algorithm to index
     * contacts by the sound of their names. This is for demonstration purposes only
     */
    private void indexContacts(List<Contact> contacts) {
        Log.d(TAG, "Indexing contact: " + (contacts != null ? contacts.size() : null));
        if (contacts == null) {
            mContactsByPhoneticKey = new HashMap<>();
            return;
        }
        Map<String, Set<Contact>> contactsForPhoneticKey = new HashMap<>();
        Consumer<Pair<String, Contact>> indexer = p -> {
            if (p.first == null) {
                return;
            }
            String phoneticKey = StringUtils.soundex(p.first.toLowerCase());
            Set<Contact> contactSet = contactsForPhoneticKey.computeIfAbsent(phoneticKey,
                    k -> new HashSet<>());
            contactSet.add(p.second);
            Log.d(TAG, String.format("Indexing contact: '%s' - word: '%s' - phonetic key: '%s'",
                    p.second.getLookupKey(), p.first.toLowerCase(), phoneticKey));
        };
        for (Contact contact : contacts) {
            indexer.accept(Pair.create(contact.getDisplayName(), contact));
            indexer.accept(Pair.create(contact.getDisplayNameAlt(), contact));
            indexer.accept(Pair.create(contact.getFamilyName(), contact));
            indexer.accept(Pair.create(contact.getGivenName(), contact));
        }

        mContactsByPhoneticKey = contactsForPhoneticKey;
    }

    /**
     * A production voice control application would apply phonetic matching to best recognize the
     * requested name. Additionally, a disambiguation flow, asking the user to select between
     * multiple potential candidates could improve accuracy.
     * This implementation just find the contact with the minimum edit distance to the recognized
     * string.
     *
     * @param deviceAddress The bluetooth device address. If provided, only return contacts from
     *                      the specified device, otherwise return all contacts.
     */
    @Nullable
    public Contact getContact(String query, @Nullable String deviceAddress) {
        String normalizedInput = query.toLowerCase().trim();
        String phoneticKey = StringUtils.soundex(normalizedInput);

        Set<Contact> contactsForPhoneticKey;
        contactsForPhoneticKey = mContactsByPhoneticKey.get(phoneticKey);
        if (deviceAddress != null) {
            contactsForPhoneticKey.removeIf(contact ->
                    !contact.getAccountName().equals(deviceAddress));
        }

        Log.d(TAG, "Device: " + deviceAddress
                + " contact query: " + query
                + " phonetic key: " + phoneticKey
                + " contacts: " + (contactsForPhoneticKey != null
                ? contactsForPhoneticKey.size() : "-"));
        if (contactsForPhoneticKey == null) {
            return null;
        }

        Contact bestMatch = null;
        int distance = Integer.MAX_VALUE;
        Log.d(TAG, "Found " + contactsForPhoneticKey.size() + " phonetic matches");
        for (Contact c : contactsForPhoneticKey) {
            int newDistance = StringUtils.getMinDistance(normalizedInput, c.getDisplayName(),
                    c.getDisplayNameAlt(), c.getFamilyName(), c.getGivenName());
            if (newDistance < distance) {
                distance = newDistance;
                bestMatch = c;
            }
        }

        Log.d(TAG, "Best match: " + bestMatch.getDisplayName());
        return bestMatch;
    }
}
