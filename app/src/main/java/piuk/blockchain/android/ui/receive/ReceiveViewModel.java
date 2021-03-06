package piuk.blockchain.android.ui.receive;

import com.google.common.collect.HashBiMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;
import android.webkit.MimeTypeMap;

import info.blockchain.wallet.payload.data.Account;
import info.blockchain.wallet.payload.data.LegacyAddress;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import piuk.blockchain.android.R;
import piuk.blockchain.android.data.datamanagers.PayloadDataManager;
import piuk.blockchain.android.data.datamanagers.QrCodeDataManager;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.ui.account.ItemAccount;
import piuk.blockchain.android.ui.base.BaseViewModel;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.AndroidUtils;
import piuk.blockchain.android.util.AppUtil;
import piuk.blockchain.android.util.BitcoinLinkGenerator;
import piuk.blockchain.android.util.MonetaryUtil;
import piuk.blockchain.android.util.PrefsUtil;
import piuk.blockchain.android.util.SSLVerifyUtil;
import piuk.blockchain.android.util.StringUtils;

@SuppressWarnings("WeakerAccess")
public class ReceiveViewModel extends BaseViewModel {

    public static final String TAG = ReceiveViewModel.class.getSimpleName();
    @VisibleForTesting static final String KEY_WARN_WATCH_ONLY_SPEND = "warn_watch_only_spend";
    private static final int DIMENSION_QR_CODE = 600;

    private DataListener dataListener;
    private ReceiveCurrencyHelper currencyHelper;

    @Inject AppUtil appUtil;
    @Inject PrefsUtil prefsUtil;
    @Inject StringUtils stringUtils;
    @Inject QrCodeDataManager qrCodeDataManager;
    @Inject WalletAccountHelper walletAccountHelper;
    @Inject SSLVerifyUtil sslVerifyUtil;
    @Inject Context applicationContext;
    @Inject PayloadDataManager payloadDataManager;
    @VisibleForTesting HashBiMap<Integer, Object> accountMap;
    @VisibleForTesting SparseIntArray spinnerIndexMap;

    public interface DataListener {

        Bitmap getQrBitmap();

        void onAccountDataChanged();

        void showQrLoading();

        void showQrCode(@Nullable Bitmap bitmap);

        void showToast(String message, @ToastCustom.ToastType String toastType);

        void updateFiatTextField(String text);

        void updateBtcTextField(String text);

        void startContactSelectionActivity();

        void updateReceiveAddress(String address);

    }

    ReceiveViewModel(DataListener listener, Locale locale) {
        Injector.getInstance().getDataManagerComponent().inject(this);
        dataListener = listener;

        int btcUnitType = prefsUtil.getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC);
        MonetaryUtil monetaryUtil = new MonetaryUtil(btcUnitType);
        currencyHelper = new ReceiveCurrencyHelper(monetaryUtil, locale);

        accountMap = HashBiMap.create();
        spinnerIndexMap = new SparseIntArray();
    }

    @Override
    public void onViewReady() {
        sslVerifyUtil.validateSSL();
        updateAccountList();
    }

    void onSendToContactClicked(String btcAmount) {
        long amountLong = currencyHelper.getLongAmount(btcAmount);
        if (amountLong > 0) {
            dataListener.startContactSelectionActivity();
        } else {
            dataListener.showToast(stringUtils.getString(R.string.invalid_amount), ToastCustom.TYPE_ERROR);
        }
    }

    @NonNull
    List<ItemAccount> getReceiveToList() {
        ArrayList<ItemAccount> itemAccounts = new ArrayList<>();
        itemAccounts.addAll(walletAccountHelper.getAccountItems(true));
        itemAccounts.addAll(walletAccountHelper.getAddressBookEntries());
        return itemAccounts;
    }

    @NonNull
    ReceiveCurrencyHelper getCurrencyHelper() {
        return currencyHelper;
    }

    @NonNull
    PrefsUtil getPrefsUtil() {
        return prefsUtil;
    }

    // TODO: 06/02/2017 This is not a nice way of doing things. We need to refactor this stuff
    // into a Map of some description and start passing around HashCodes maybe.
    int getObjectPosition(Object object) {
        for (Object item : accountMap.values()) {
            if (object instanceof Account && item instanceof Account) {
                if (((Account) object).getXpub().equals(((Account) item).getXpub())) {
                    return accountMap.inverse().get(item);
                }
            } else if (object instanceof LegacyAddress && item instanceof LegacyAddress) {
                if (((LegacyAddress) object).getAddress().equals(((LegacyAddress) item).getAddress())) {
                    return accountMap.inverse().get(item);
                }
            }
        }
        return getDefaultAccountPosition();
    }

    int getCorrectedAccountIndex(int accountIndex) {
        // Filter accounts by active
        List<Account> activeAccounts = new ArrayList<>();
        List<Account> accounts = payloadDataManager.getWallet().getHdWallets().get(0).getAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            if (!account.isArchived()) {
                activeAccounts.add(account);
            }
        }

        // Find corrected position
        return payloadDataManager.getWallet().getHdWallets().get(0).getAccounts().indexOf(activeAccounts.get(accountIndex));
    }

    void updateAccountList() {
        accountMap.clear();
        spinnerIndexMap.clear();
        int spinnerIndex = 0;
        // V3
        List<Account> accounts = payloadDataManager.getWallet().getHdWallets().get(0).getAccounts();
        int accountIndex = 0;
        for (Account item : accounts) {
            spinnerIndexMap.put(spinnerIndex, accountIndex);
            accountIndex++;
            if (item.isArchived())
                // Skip archived account
                continue;

            accountMap.put(spinnerIndex, item);
            spinnerIndex++;
        }

        // Legacy Addresses
        List<LegacyAddress> legacyAddresses = payloadDataManager.getWallet().getLegacyAddressList();
        for (LegacyAddress legacyAddress : legacyAddresses) {
            if (legacyAddress.getTag() == LegacyAddress.ARCHIVED_ADDRESS)
                // Skip archived address
                continue;

            accountMap.put(spinnerIndex, legacyAddress);
            spinnerIndex++;
        }

        dataListener.onAccountDataChanged();
    }

    void generateQrCode(String uri) {
        dataListener.showQrLoading();
        compositeDisposable.clear();
        compositeDisposable.add(
                qrCodeDataManager.generateQrCode(uri, DIMENSION_QR_CODE)
                        .subscribe(
                                qrCode -> dataListener.showQrCode(qrCode),
                                throwable -> dataListener.showQrCode(null)));
    }

    int getDefaultAccountPosition() {
        return accountMap.inverse().get(getDefaultAccount());
    }

    @Nullable
    Object getAccountItemForPosition(int position) {
        return accountMap.get(position);
    }

    boolean warnWatchOnlySpend() {
        return prefsUtil.getValue(KEY_WARN_WATCH_ONLY_SPEND, true);
    }

    void setWarnWatchOnlySpend(boolean warn) {
        prefsUtil.setValue(KEY_WARN_WATCH_ONLY_SPEND, warn);
    }

    void updateFiatTextField(String bitcoin) {
        if (bitcoin.isEmpty()) bitcoin = "0";
        double btcAmount = currencyHelper.getUndenominatedAmount(currencyHelper.getDoubleAmount(bitcoin));
        double fiatAmount = currencyHelper.getLastPrice() * btcAmount;
        dataListener.updateFiatTextField(currencyHelper.getFormattedFiatString(fiatAmount));
    }

    void updateBtcTextField(String fiat) {
        if (fiat.isEmpty()) fiat = "0";
        double fiatAmount = currencyHelper.getDoubleAmount(fiat);
        double btcAmount = fiatAmount / currencyHelper.getLastPrice();
        dataListener.updateBtcTextField(currencyHelper.getFormattedBtcString(btcAmount));
    }

    void getV3ReceiveAddress(Account account) {
        compositeDisposable.add(
                payloadDataManager.getNextReceiveAddress(account)
                        .subscribe(
                                address -> dataListener.updateReceiveAddress(address),
                                throwable -> dataListener.showToast(stringUtils.getString(R.string.unexpected_error), ToastCustom.TYPE_ERROR)));
    }

    @Nullable
    List<SendPaymentCodeData> getIntentDataList(String uri) {
        File file = getQrFile();
        FileOutputStream outputStream;
        outputStream = getFileOutputStream(file);

        if (outputStream != null) {
            Bitmap bitmap = dataListener.getQrBitmap();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream);

            try {
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "getIntentDataList: ", e);
                dataListener.showToast(e.getMessage(), ToastCustom.TYPE_ERROR);
                return null;
            }

            List<SendPaymentCodeData> dataList = new ArrayList<>();

            PackageManager packageManager = appUtil.getPackageManager();

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setType("application/image");
            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

            if (getFormattedEmailLink(uri) != null) {
                emailIntent.setData(getFormattedEmailLink(uri));
            } else {
                dataListener.showToast(stringUtils.getString(R.string.unexpected_error), ToastCustom.TYPE_ERROR);
                return null;
            }

            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            String type = mime.getMimeTypeFromExtension(ext);

            Intent imageIntent = new Intent();
            imageIntent.setAction(Intent.ACTION_SEND);
            imageIntent.setType(type);

            if (AndroidUtils.is23orHigher()) {
                Uri uriForFile = FileProvider.getUriForFile(applicationContext, appUtil.getPackageName() + ".fileProvider", file);
                imageIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                imageIntent.putExtra(Intent.EXTRA_STREAM, uriForFile);
            } else {
                imageIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                imageIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            HashMap<String, Pair<ResolveInfo, Intent>> intentHashMap = new HashMap<>();

            List<ResolveInfo> emailInfos = packageManager.queryIntentActivities(emailIntent, 0);
            addResolveInfoToMap(emailIntent, intentHashMap, emailInfos);

            List<ResolveInfo> imageInfos = packageManager.queryIntentActivities(imageIntent, 0);
            addResolveInfoToMap(imageIntent, intentHashMap, imageInfos);

            SendPaymentCodeData d;

            Iterator it = intentHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry mapItem = (Map.Entry) it.next();
                @SuppressWarnings("unchecked") Pair<ResolveInfo, Intent> pair =
                        (Pair<ResolveInfo, Intent>) mapItem.getValue();
                ResolveInfo resolveInfo = pair.first;
                String context = resolveInfo.activityInfo.packageName;
                String packageClassName = resolveInfo.activityInfo.name;
                CharSequence label = resolveInfo.loadLabel(packageManager);
                Drawable icon = resolveInfo.loadIcon(packageManager);

                Intent intent = pair.second;
                intent.setClassName(context, packageClassName);

                d = new SendPaymentCodeData(label.toString(), icon, intent);
                dataList.add(d);

                it.remove();
            }

            return dataList;

        } else {
            dataListener.showToast(stringUtils.getString(R.string.unexpected_error), ToastCustom.TYPE_ERROR);
            return null;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("SetWorldReadable")
    private File getQrFile() {
        String strFileName = appUtil.getReceiveQRFilename();
        File file = new File(strFileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                Log.e(TAG, "getQrFile: ", e);
            }
        }
        file.setReadable(true, false);
        return file;
    }

    @Nullable
    private FileOutputStream getFileOutputStream(File file) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "getFileOutputStream: ", e);
        }
        return fos;
    }

    @Nullable
    private Uri getFormattedEmailLink(String uri) {
        try {
            BitcoinURI addressUri = new BitcoinURI(uri);
            String amount = addressUri.getAmount() != null ? " " + addressUri.getAmount().toPlainString() : "";
            String address = addressUri.getAddress() != null ? addressUri.getAddress().toString() : stringUtils.getString(R.string.email_request_body_fallback);
            String body = String.format(stringUtils.getString(R.string.email_request_body), amount, address);

            String builder = "mailto:" +
                    "?subject=" +
                    stringUtils.getString(R.string.email_request_subject) +
                    "&body=" +
                    body +
                    '\n' +
                    '\n' +
                    BitcoinLinkGenerator.getLink(addressUri);

            return Uri.parse(builder);

        } catch (BitcoinURIParseException e) {
            Log.e(TAG, "getFormattedEmailLink: ", e);
            return null;
        }
    }

    private Account getDefaultAccount() {
        return payloadDataManager.getDefaultAccount();
    }

    /**
     * Prevents apps being added to the list twice, as it's confusing for users. Full email Intent
     * takes priority.
     */
    private void addResolveInfoToMap(Intent intent, HashMap<String, Pair<ResolveInfo, Intent>> intentHashMap, List<ResolveInfo> resolveInfo) {
        //noinspection Convert2streamapi
        for (ResolveInfo info : resolveInfo) {
            if (!intentHashMap.containsKey(info.activityInfo.name)) {
                intentHashMap.put(info.activityInfo.name, new Pair<>(info, new Intent(intent)));
            }
        }
    }

    static class SendPaymentCodeData {
        private Drawable logo;
        private String title;
        private Intent intent;

        SendPaymentCodeData(String title, Drawable logo, Intent intent) {
            this.title = title;
            this.logo = logo;
            this.intent = intent;
        }

        public Intent getIntent() {
            return intent;
        }

        public String getTitle() {
            return title;
        }

        public Drawable getLogo() {
            return logo;
        }
    }
}
