package piuk.blockchain.android.ui.send;

import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import piuk.blockchain.android.data.contacts.PaymentRequestType;
import piuk.blockchain.android.ui.account.PaymentConfirmationDetails;
import piuk.blockchain.android.ui.customviews.ToastCustom;

interface SendContract {

    interface DataListener {

        Bundle getFragmentBundle();

        @Nullable
        String getClipboardContents();

        void hideSendingAddressField();

        void hideReceivingAddressField();

        void updateBtcAmount(String amount);

        void updateBtcUnit(String unit);

        void updateFiatUnit(String unit);

        void onSetSpendAllAmount(String textFromSatoshis);

        void showInvalidAmount();

        void onShowSpendFromWatchOnly(String address);

        void updateBtcTextField(String text);

        void updateFiatTextField(String text);

        void onShowPaymentDetails(PaymentConfirmationDetails confirmationDetails);

        void onShowReceiveToWatchOnlyWarning(String address);

        void showToast(@StringRes int message, @ToastCustom.ToastType String toastType);

        void onShowTransactionSuccess(String hash, long transactionValue);

        void onShowBIP38PassphrasePrompt(String scanData);

        void finishPage();

        void setContactName(String name);

        void showProgressDialog();

        void dismissProgressDialog();

        void setDestinationAddress(String btcAddress);

        void setMaxAvailable(String max);

        void setMaxAvailableColor(@ColorRes int color);

        void setMaxAvailableVisible(boolean visible);

        void setUnconfirmedFunds(String notice);

        void showSecondPasswordDialog();

        void navigateToAddNote(String contactId, PaymentRequestType paymentRequestType, long satoshis);

        void dismissConfirmationDialog();

        void updateFeeField(String fee);

        long getCustomFeeValue();
    }

}
