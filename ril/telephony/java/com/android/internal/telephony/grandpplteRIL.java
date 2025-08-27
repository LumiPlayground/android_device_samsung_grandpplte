/*
 * Copyright (c) 2014-2016, The CyanogenMod Project. All rights reserved.
 * Copyright (c) 2025, The LineageOS Project. All rights reserved.
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

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.telephony.Rlog;
import android.os.AsyncResult;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;

/**
 * RIL customization for Galaxy J2 Prime/Grand Prime Plus
 *
 * {@hide}
 */

public class grandpplteRIL extends RIL implements CommandsInterface {

    /**********************************************************
     * SAMSUNG REQUESTS
     **********************************************************/
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    
    /*request*/
    private static final int RIL_REQUEST_DIAL_EMERGENCY_CALL = 10001;

    /*response*/
    private static final int RIL_UNSOL_STK_SEND_SMS_RESULT = 11002;
    private static final int RIL_UNSOL_STK_CALL_CONTROL_RESULT = 11003;
    private static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    private static final int RIL_UNSOL_AM = 11010;
    private static final int RIL_UNSOL_SIM_PB_READY = 11021;
    private static final int RIL_UNSOL_PB_INIT_COMPLETE = 11035;

    public grandpplteRIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public grandpplteRIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
    }

    @Override
    public void setDataAllowed(boolean allowed, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ALLOW_DATA, result);

        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) +
                    " allowed: " + allowed);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(allowed ? 1 : 0);

        send(rr);
    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        status = translateStatus(status);

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_WRITE_SMS_TO_SIM,
                response);

        rr.mParcel.writeInt(status);
        rr.mParcel.writeString(pdu);
        rr.mParcel.writeString(smsc);

        send(rr);
    }

    @Override
    public void
    acceptCall(Message result) {
        acceptCall(0, result);
    }

    public void
    acceptCall(int index, Message result) {
        RILRequest rr =
            RILRequest.obtain(RIL_REQUEST_ANSWER, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);

        send(rr);
    }

    @Override
    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    @Override
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);
        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);

        rr.mParcel.writeInt(0);     // CallDetails.call_type
        rr.mParcel.writeInt(1);     // CallDetails.call_domain
        rr.mParcel.writeString(""); // CallDetails.getCsvFromExtras

        if (uusInfo == null) {
            rr.mParcel.writeInt(0);
        } else {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }

        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override
    protected Object responseIccCardStatus(Parcel p) {
        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();
        int numApplications = p.readInt();

        if (numApplications > 8) {
            numApplications = 8;
        }

        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];

        for (int i = 0; i < numApplications; i++) {
            IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
            appStatus.app_type = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid = p.readString();
            appStatus.app_label = p.readString();
            appStatus.pin1_replaced = p.readInt();
            appStatus.pin1 = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2 = appStatus.PinStateFromRILInt(p.readInt());

            p.readInt(); // pin1_num_retries
            p.readInt(); // puk1_num_retries
            p.readInt(); // pin2_num_retries
            p.readInt(); // puk2_num_retries
            p.readInt(); // perso_unblock_retries
            cardStatus.mApplications[i] = appStatus;
        }
        return cardStatus;
    }

    @Override
    public Object responseCallList(Parcel p) {
        ArrayList<DriverCall> response;
        DriverCall dc;

        int num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0; i < num; i++) {
            dc = new DriverCall();

            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt() & 255;
            dc.TOA = p.readInt();
            dc.isMpty = p.readInt() != 0;
            dc.isMT = p.readInt() != 0;
            dc.als = p.readInt();
            int voiceSettings = p.readInt();
            dc.isVoice = voiceSettings != 0;
            int type = p.readInt();
            int domain = p.readInt();
            String extras = p.readString();
            dc.isVoicePrivacy = p.readInt() != 0;
            dc.number = p.readString();
            int np = p.readInt();
            dc.numberPresentation = DriverCall.presentationFromCLIP(np);
            dc.name = p.readString();
            Rlog.d(RILJ_LOG_TAG, "responseCallList dc.name" + dc.name);
            dc.namePresentation = DriverCall.presentationFromCLIP(p.readInt());
            int uusInfoPresent = p.readInt();
            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d",
                                dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                                dc.uusInfo.getUserData().length));
                riljLogv("Incoming UUS : data (string)="
                        + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): "
                        + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }

            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

            response.add(dc);

            if (dc.isVoicePrivacy) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }

        Collections.sort(response);

        if ((num == 0) && mTestingEmergencyCall.getAndSet(false)) {
            if (mEmergencyCallbackModeRegistrant != null) {
                riljLog("responseCallList: call ended, testing emergency call," +
                            " notify ECM Registrants");
                mEmergencyCallbackModeRegistrant.notifyRegistrant();
            }
        }

        return response;
    }

    /**
     *  Translates EF_SMS status bits to a status value compatible with
     *  SMS AT commands.  See TS 27.005 3.1.
     */
    private int translateStatus(int status) {
        switch(status & 0x7) {
            case SmsManager.STATUS_ON_ICC_READ:
                return 1;
            case SmsManager.STATUS_ON_ICC_UNREAD:
                return 0;
            case SmsManager.STATUS_ON_ICC_SENT:
                return 3;
            case SmsManager.STATUS_ON_ICC_UNSENT:
                return 2;
        }
        // Default to READ.
        return 1;
    }
    
    @Override
    protected Object responseOperatorInfos(Parcel p) {
        ArrayList<OperatorInfo> ret;
        String[] strings = (String[])responseStrings(p);

        if (strings.length % 6 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multible of 6");
        }
	
       ret = new ArrayList<OperatorInfo>(strings.length / 6);
    
       for (int i = 0; i < strings.length; i += 6) {
            String strOperatorLong = strings[i+0];
            String strOperatorNumeric = strings[i+2];
            String strState = strings[i+3].toLowerCase();

            ret.add (
                new OperatorInfo(
                    strOperatorLong, // operatorAlphaLong
                    strOperatorLong, // operatorAlphaShort
                    strOperatorNumeric,    // operatorNumeric
                    strState));  // stateString

            Rlog.v(RILJ_LOG_TAG,
                   "grandpplteRIL: Add OperatorInfo: " + strOperatorLong +
                   ", " + strOperatorLong +
                   ", " + strOperatorNumeric +
                   ", " + strState);
       }
        
       return ret;
    }

    @Override
    protected void processUnsolicited(Parcel p, int type) {
        Object ret;

        int dataPosition = p.dataPosition();
        int origResponse = p.readInt();
        int newResponse = origResponse;

        // Remap incorrect respones or ignore them
        switch (origResponse) {
            case RIL_UNSOL_STK_CALL_CONTROL_RESULT:
            case RIL_UNSOL_SIM_PB_READY: // Registrant notification 
            case RIL_UNSOL_DEVICE_READY_NOTI:
        	Rlog.v(RILJ_LOG_TAG,
                       "grandpplteRIL: ignoring unsolicited response " +
                       origResponse);
                return;
        }

        if (newResponse != origResponse) {
            riljLog("grandpplteRIL: remap unsolicited response from " +
                    origResponse + " to " + newResponse);
            p.setDataPosition(dataPosition);
            p.writeInt(newResponse);
        }

        switch (newResponse) {
            case RIL_UNSOL_AM:
                ret = responseString(p);
                break;
            case RIL_UNSOL_STK_SEND_SMS_RESULT:
                ret = responseInts(p);
                break;
            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p, type);
                return;
        }
	
        switch (newResponse) {
            case RIL_UNSOL_AM:
                String strAm = (String)ret;
                Rlog.v(RILJ_LOG_TAG, "grandpplteRIL: am=" + strAm);
                break;
        }
    }
}
