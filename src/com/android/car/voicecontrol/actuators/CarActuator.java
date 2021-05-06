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

package com.android.car.voicecontrol.actuators;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaWheel;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import com.android.car.voicecontrol.R;
import com.android.car.voicecontrol.TextToSpeech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Actuator that interacts with the vehicle properties
 */
public class CarActuator implements SkillRouter.Actuator {
    private Context mContext;
    private Car mCar;
    private CarPropertyManager mCarPropertyManager;
    private float mSpeedConversionFactor;
    private String mSpeedUnit;
    private float mPressureConversionFactor;
    private String mPressureUnit;

    /** Area identifier used for sensors corresponding to global VHAL properties */
    private static final int GLOBAL_AREA_ID = 0;

    private enum Zone {
        FRONT(
                VehicleAreaWheel.WHEEL_LEFT_FRONT | VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                VehicleAreaSeat.SEAT_ROW_1_CENTER | VehicleAreaSeat.SEAT_ROW_1_LEFT
                        | VehicleAreaSeat.SEAT_ROW_1_RIGHT
        ),
        BACK(
                VehicleAreaWheel.WHEEL_LEFT_REAR | VehicleAreaWheel.WHEEL_RIGHT_REAR,
                VehicleAreaSeat.SEAT_ROW_2_LEFT | VehicleAreaSeat.SEAT_ROW_2_CENTER
                | VehicleAreaSeat.SEAT_ROW_2_RIGHT | VehicleAreaSeat.SEAT_ROW_3_LEFT
                | VehicleAreaSeat.SEAT_ROW_3_CENTER | VehicleAreaSeat.SEAT_ROW_3_RIGHT
        ),
        ;
        public final int mWheelAreasBitmap;
        public final int mSeatAreasBitmap;

        Zone(int wheelAreasBitmap, int seatAreasBitmap) {
            mWheelAreasBitmap = wheelAreasBitmap;
            mSeatAreasBitmap = seatAreasBitmap;
        }
    }

    @Override
    public List<Skill> onCreate(Context context) {
        mContext = context;
        mCar = Car.createCar(context);
        mCarPropertyManager = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        mSpeedConversionFactor = mContext.getResources()
                .getFloat(R.dimen.property_speed_conversion);
        mSpeedUnit = mContext.getString(R.string.property_speed_unit);
        mPressureConversionFactor = mContext.getResources()
                .getFloat(R.dimen.property_pressure_conversion);
        mPressureUnit = mContext.getString(R.string.property_pressure_unit);

        return Arrays.asList(
                new Skill("turn.* ac (on|off)( at the (back|front))?",
                        (args, tts) -> changeAC("on".equals(args[1]), tts, args[3])),
                new Skill("what.* pressure.* tires",
                        (args, tts) -> informTirePressure(tts)),
                new Skill("what.* my speed", (args, tts) -> informSpeed(tts))
        );
    }

    @Override
    public void onDestroy() {
        mCar.disconnect();
    }

    private boolean changeAC(boolean turnOn, TextToSpeech tts, String zoneName) {
        CarPropertyConfig config = mCarPropertyManager
                .getCarPropertyConfig(VehiclePropertyIds.HVAC_AC_ON);
        if (config == null) {
            tts.speak(R.string.speech_reply_cannot_control_ac);
            return true;
        }

        // If zone is not provided, we assume it is the front seats.
        Zone zone = zoneName != null ? Zone.valueOf(zoneName.toUpperCase()) : Zone.FRONT;
        List<Integer> areasToChange = new ArrayList<>();
        for (int areaId : config.getAreaIds()) {
            if ((areaId & zone.mSeatAreasBitmap) == 0) {
                continue;
            }
            // Check which areas we need to actually change
            boolean isACInAreaOn = mCarPropertyManager
                    .getBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, areaId);
            if (isACInAreaOn != turnOn) {
                areasToChange.add(areaId);
            }
        }
        if (areasToChange.isEmpty()) {
            tts.speak(turnOn ? R.string.speech_reply_ac_is_already_on
                    : R.string.speech_reply_ac_is_already_off);
            return true;
        }

        for (int areaId : areasToChange) {
            mCarPropertyManager.setBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, areaId, turnOn);
        }
        tts.speak(turnOn ? R.string.speech_reply_turning_ac_on
                : R.string.speech_reply_turning_ac_off);
        return false;
    }

    private boolean informSpeed(TextToSpeech tts) {
        if (!mCarPropertyManager.isPropertyAvailable(VehiclePropertyIds.PERF_VEHICLE_SPEED,
                GLOBAL_AREA_ID)) {
            tts.speak(R.string.speech_reply_cannot_read_speed);
            return true;
        }
        float speedInMetersPerSecond = mCarPropertyManager.getFloatProperty(
                VehiclePropertyIds.PERF_VEHICLE_SPEED, GLOBAL_AREA_ID);
        int speedInUserUnit = (int) (speedInMetersPerSecond * mSpeedConversionFactor);
        tts.speak(R.string.speech_reply_speed_value, speedInUserUnit, mSpeedUnit);
        return true;
    }

    private boolean informTirePressure(TextToSpeech tts) {
        CarPropertyConfig config = mCarPropertyManager
                .getCarPropertyConfig(VehiclePropertyIds.TIRE_PRESSURE);
        if (config == null) {
            tts.speak(R.string.speech_reply_cannot_read_tire_pressure);
            return true;
        }

        // Retrieve areas this property is available on, and classify them by front and rear.
        List<Integer> frontTirePressure = new ArrayList<>();
        List<Integer> rearTirePressure = new ArrayList<>();
        for (int areaId : config.getAreaIds()) {
            float pressureKPA = mCarPropertyManager.getFloatProperty(
                    VehiclePropertyIds.TIRE_PRESSURE, areaId);
            int pressureUserUnit = (int) (pressureKPA * mPressureConversionFactor);
            if ((areaId & Zone.FRONT.mWheelAreasBitmap) != 0) {
                frontTirePressure.add(pressureUserUnit);
            } else {
                rearTirePressure.add(pressureUserUnit);
            }
        }

        // This simplified logic assumes there are values for both front and rear.
        tts.speak(R.string.speech_reply_tire_pressure_value, integerListToString(frontTirePressure),
                mPressureUnit, integerListToString(rearTirePressure), mPressureUnit);
        return true;
    }

    private String integerListToString(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0 && i < (values.size() - 1)) {
                sb.append(mContext.getString(R.string.speech_reply_tire_pressure_separator));
            } else if (i == (values.size() - 1)) {
                sb.append(mContext.getString(R.string.speech_reply_tire_pressure_last_separator));
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
