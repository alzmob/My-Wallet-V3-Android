package piuk.blockchain.android.ui.contacts.detail

import android.app.Application
import android.os.Bundle
import com.nhaarman.mockito_kotlin.*
import info.blockchain.wallet.contacts.data.Contact
import info.blockchain.wallet.contacts.data.FacilitatedTransaction
import info.blockchain.wallet.contacts.data.PaymentRequest
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.HDWallet
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.data.contacts.ContactTransactionModel
import piuk.blockchain.android.data.datamanagers.ContactsDataManager
import piuk.blockchain.android.data.datamanagers.PayloadDataManager
import piuk.blockchain.android.data.notifications.NotificationPayload
import piuk.blockchain.android.data.rxjava.RxBus
import piuk.blockchain.android.injection.*
import piuk.blockchain.android.ui.contacts.list.ContactsListActivity.KEY_BUNDLE_CONTACT_ID
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.util.MonetaryUtil
import piuk.blockchain.android.util.PrefsUtil
import piuk.blockchain.android.util.StringUtils
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

@Config(sdk = intArrayOf(23), constants = BuildConfig::class, application = BlockchainTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class ContactDetailViewModelTest {

    private lateinit var subject: ContactDetailViewModel
    private val mockActivity: ContactDetailViewModel.DataListener = mock()
    private val mockContactsManager: ContactsDataManager = mock()
    private val mockPayloadDataManager: PayloadDataManager = mock()
    private val mockPrefsUtil: PrefsUtil = mock()
    private val mockRxBus: RxBus = mock()

    @Before
    @Throws(Exception::class)
    fun setUp() {

        InjectorTestUtils.initApplicationComponent(
                Injector.getInstance(),
                MockApplicationModule(RuntimeEnvironment.application),
                MockApiModule(),
                MockDataManagerModule())

        subject = ContactDetailViewModel(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadyShouldFinishPage() {
        // Arrange
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        // Act
        subject.onViewReady()
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadyShouldThrowErrorAndQuitPage() {
        // Arrange
        val contactId = "CONTACT_ID"
        val bundle = Bundle()
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        bundle.putString(KEY_BUNDLE_CONTACT_ID, contactId)
        whenever(mockActivity.pageBundle).thenReturn(bundle)
        whenever(mockContactsManager.contactList)
                .thenReturn(Observable.error { Throwable() })
        // Act
        subject.onViewReady()
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity, times(2)).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadyShouldSucceed() {
        // Arrange
        val contactId = "CONTACT_ID"
        val bundle = Bundle()
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        bundle.putString(KEY_BUNDLE_CONTACT_ID, contactId)
        whenever(mockActivity.pageBundle).thenReturn(bundle)
        val contactName = "CONTACT_NAME"
        val contact0 = Contact()
        val contact1 = Contact()
        val contact2 = Contact().apply {
            id = contactId
            name = contactName
        }
        whenever(mockContactsManager.contactList)
                .thenReturn(Observable.fromIterable(listOf(contact0, contact1, contact2)))
        whenever(mockContactsManager.fetchContacts()).thenReturn(Completable.complete())
        // Act
        subject.onViewReady()
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).updateContactName(contactName)
        verify(mockActivity, times(2)).onTransactionsUpdated(any())
        verifyNoMoreInteractions(mockActivity)
        verify(mockContactsManager).contactList
        verify(mockContactsManager).fetchContacts()
        verifyNoMoreInteractions(mockContactsManager)
        subject.contact shouldEqual contact2
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadySubscribeAndEmitEvent() {
        // Arrange
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        val notificationPayload: NotificationPayload = mock()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        whenever(notificationPayload.type).thenReturn(NotificationPayload.NotificationType.PAYMENT)
        // Act
        subject.onViewReady()
        notificationObservable.onNext(notificationPayload)
        // Assert
        verify(mockActivity, atLeastOnce()).pageBundle
        verify(mockActivity, times(2)).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity, times(2)).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadySubscribeAndEmitUnwantedEvent() {
        // Arrange
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        val notificationPayload: NotificationPayload = mock()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        whenever(notificationPayload.type).thenReturn(NotificationPayload.NotificationType.CONTACT_REQUEST)
        // Act
        subject.onViewReady()
        notificationObservable.onNext(notificationPayload)
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadySubscribeAndEmitNullEvent() {
        // Arrange
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        val notificationPayload: NotificationPayload = mock()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        // Act
        subject.onViewReady()
        notificationObservable.onNext(notificationPayload)
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadySubscribeAndEmitErrorEvent() {
        // Arrange
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        // Act
        subject.onViewReady()
        notificationObservable.onError(Throwable())
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun getPrefsUtil() {
        // Arrange

        // Act
        val result = subject.getPrefsUtil()
        // Assert
        result shouldEqual mockPrefsUtil
    }

    @Test
    @Throws(Exception::class)
    fun getContactsTransactionMap() {
        // Arrange
        whenever(mockContactsManager.contactsTransactionMap).thenReturn(HashMap())
        // Act
        val result = subject.contactsTransactionMap
        // Assert
        verify(mockContactsManager).contactsTransactionMap
        result shouldBeInstanceOf HashMap::class
    }

    @Test
    @Throws(Exception::class)
    fun getNotesTransactionMap() {
        // Arrange
        whenever(mockContactsManager.notesTransactionMap).thenReturn(HashMap())
        // Act
        val result = subject.notesTransactionMap
        // Assert
        verify(mockContactsManager).notesTransactionMap
        result shouldBeInstanceOf HashMap::class
    }

    @Test
    @Throws(Exception::class)
    fun onDeleteContactClicked() {
        // Arrange

        // Act
        subject.onDeleteContactClicked()
        // Assert
        verify(mockActivity).showDeleteUserDialog()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onDeleteContactConfirmedShouldShowSuccessful() {
        // Arrange
        val contact = Contact()
        whenever(mockContactsManager.removeContact(contact)).thenReturn(Completable.complete())
        subject.contact = contact
        // Act
        subject.onDeleteContactConfirmed()
        // Assert
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_OK))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
        verify(mockContactsManager).removeContact(contact)
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun onDeleteContactConfirmedShouldShowError() {
        // Arrange
        val contact = Contact()
        whenever(mockContactsManager.removeContact(contact))
                .thenReturn(Completable.error { Throwable() })
        subject.contact = contact
        // Act
        subject.onDeleteContactConfirmed()
        // Assert
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verifyNoMoreInteractions(mockActivity)
        verify(mockContactsManager).removeContact(contact)
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun onRenameContactClicked() {
        // Arrange
        val contact = Contact().apply { name = "CONTACT_NAME" }
        subject.contact = contact
        // Act
        subject.onRenameContactClicked()
        // Assert
        verify(mockActivity).showRenameDialog(contact.name)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onContactRenamedShouldDoNothingAsNameMatches() {
        // Arrange
        val contactName = "CONTACT_NAME"
        val contact = Contact().apply { name = contactName }
        subject.contact = contact
        // Act
        subject.onContactRenamed(contactName)
        // Assert
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onContactRenamedShouldShowErrorAsNameEmpty() {
        // Arrange
        val emptyName = ""
        val contact = Contact().apply { name = "CONTACT_NAME" }
        subject.contact = contact
        // Act
        subject.onContactRenamed(emptyName)
        // Assert
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onContactRenamedShouldShowErrorAsWebCallFails() {
        // Arrange
        val newName = "CONTACT_NAME"
        val contactId = "CONTACT_ID"
        val contact = Contact().apply {
            name = ""
            id = contactId
        }
        subject.contact = contact
        whenever(mockContactsManager.renameContact(contactId, newName))
                .thenReturn(Completable.error { Throwable() })
        // Act
        subject.onContactRenamed(newName)
        // Assert
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verifyNoMoreInteractions(mockActivity)
        verify(mockContactsManager).renameContact(contactId, newName)
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun onContactRenamedShouldShowSuccess() {
        // Arrange
        val newName = "NEW_NAME"
        val oldName = "OLD_NAME"
        val contactId = "CONTACT_ID"
        val contact = Contact().apply {
            name = oldName
            id = contactId
        }
        subject.contact = contact
        val bundle = Bundle()
        bundle.putString(KEY_BUNDLE_CONTACT_ID, contactId)
        whenever(mockActivity.pageBundle).thenReturn(bundle)
        whenever(mockContactsManager.renameContact(contactId, newName))
                .thenReturn(Completable.complete())
        whenever(mockContactsManager.contactList).thenReturn(Observable.just(contact))
        whenever(mockContactsManager.fetchContacts()).thenReturn(Completable.complete())
        val notificationObservable = PublishSubject.create<NotificationPayload>()
        whenever(mockRxBus.register(NotificationPayload::class.java)).thenReturn(notificationObservable)
        // Act
        subject.onContactRenamed(newName)
        // Assert
        verify(mockActivity).pageBundle
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).updateContactName(oldName)
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_OK))
        verify(mockActivity, times(2)).onTransactionsUpdated(any())
        verifyNoMoreInteractions(mockActivity)
        verify(mockContactsManager).renameContact(contactId, newName)
        verify(mockContactsManager).contactList
        verify(mockContactsManager).fetchContacts()
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowNotFound() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowWaitingForAddress() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            state = FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS
            role = FacilitatedTransaction.ROLE_RPR_INITIATOR
        })
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).showWaitingForAddressDialog()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowWaitingForPayment() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            state = FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT
            role = FacilitatedTransaction.ROLE_PR_INITIATOR
        })
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).showWaitingForPaymentDialog()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowTxDetail() {
        // Arrange
        val txHash = "TX_HASH"
        val transactionPosition = 0
        val summary = TransactionSummary().apply { hash = txHash }
        subject.displayList.add(summary)
        // Act
        subject.onCompletedTransactionClicked(transactionPosition)
        // Assert
        verify(mockActivity).showTransactionDetail(txHash)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowSendAddressDialog() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            state = FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS
            role = FacilitatedTransaction.ROLE_PR_RECEIVER
        })
        val mockPayload: Wallet = mock()
        whenever(mockPayloadDataManager.wallet).thenReturn(mockPayload)
        val mockHdWallet: HDWallet = mock()
        whenever(mockPayload.hdWallets).thenReturn(listOf(mockHdWallet))
        val account0 = Account().apply { isArchived = true }
        val account1 = Account().apply { isArchived = true }
        val account2 = Account()
        whenever(mockHdWallet.accounts).thenReturn(listOf(account0, account1, account2))
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).showSendAddressDialog(fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldShowSendAccountChoiceDialog() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            state = FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS
            role = FacilitatedTransaction.ROLE_PR_RECEIVER
        })
        val mockPayload: Wallet = mock()
        whenever(mockPayloadDataManager.wallet).thenReturn(mockPayload)
        val mockHdWallet: HDWallet = mock()
        whenever(mockPayload.hdWallets).thenReturn(listOf(mockHdWallet))
        val accountLabel0 = "ACCOUNT_0"
        val account0 = Account().apply { label = accountLabel0 }
        val accountLabel1 = "ACCOUNT_1"
        val account1 = Account().apply { label = accountLabel1 }
        val accountLabel2 = "ACCOUNT_2"
        val account2 = Account().apply { label = accountLabel2 }
        whenever(mockHdWallet.accounts).thenReturn(listOf(account0, account1, account2))
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).showAccountChoiceDialog(listOf(accountLabel0, accountLabel1, accountLabel2), fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionClickedShouldInitiatePayment() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contact = Contact()
        subject.contact = contact
        val facilitatedTransaction = FacilitatedTransaction().apply {
            id = fctxId
            state = FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT
            role = FacilitatedTransaction.ROLE_RPR_RECEIVER
            intendedAmount = 0L
            address = ""
        }
        contact.addFacilitatedTransaction(facilitatedTransaction)
        val mockPayload: Wallet = mock()
        whenever(mockPayloadDataManager.wallet).thenReturn(mockPayload)
        val mockHdWallet: HDWallet = mock()
        whenever(mockPayload.hdWallets).thenReturn(listOf(mockHdWallet))
        // Act
        subject.onTransactionClicked(fctxId)
        // Assert
        verify(mockActivity).initiatePayment(
                facilitatedTransaction.toBitcoinURI(),
                contact.id,
                contact.mdid,
                fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionLongClickedError() {
        // Arrange
        val fctxId = "FCTX_ID"
        whenever(mockContactsManager.facilitatedTransactions)
                .thenReturn(Observable.error { Throwable() })
        // Act
        subject.onTransactionLongClicked(fctxId)
        // Assert
        verify(mockContactsManager).facilitatedTransactions
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        verify(mockActivity).finishPage()
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionLongClickedWaitingForAddressPrRec() {
        // Arrange
        val fctxId = "FCTX_ID"
        val transaction = FacilitatedTransaction().apply {
            id = fctxId
            role = FacilitatedTransaction.ROLE_PR_RECEIVER
            state = FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS
        }
        val contactTransaction = ContactTransactionModel("", transaction)
        whenever(mockContactsManager.facilitatedTransactions)
                .thenReturn(Observable.fromIterable(listOf(contactTransaction)))
        // Act
        subject.onTransactionLongClicked(fctxId)
        // Assert
        verify(mockContactsManager).facilitatedTransactions
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockActivity).showTransactionDeclineDialog(fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionLongClickedWaitingForAddressRprInit() {
        // Arrange
        val fctxId = "FCTX_ID"
        val transaction = FacilitatedTransaction().apply {
            id = fctxId
            role = FacilitatedTransaction.ROLE_RPR_INITIATOR
            state = FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS
        }
        val contactTransaction = ContactTransactionModel("", transaction)
        whenever(mockContactsManager.facilitatedTransactions)
                .thenReturn(Observable.fromIterable(listOf(contactTransaction)))
        // Act
        subject.onTransactionLongClicked(fctxId)
        // Assert
        verify(mockContactsManager).facilitatedTransactions
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockActivity).showTransactionCancelDialog(fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionLongClickedWaitingForPaymentRprRec() {
        // Arrange
        val fctxId = "FCTX_ID"
        val transaction = FacilitatedTransaction().apply {
            id = fctxId
            role = FacilitatedTransaction.ROLE_RPR_RECEIVER
            state = FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT
        }
        val contactTransaction = ContactTransactionModel("", transaction)
        whenever(mockContactsManager.facilitatedTransactions)
                .thenReturn(Observable.fromIterable(listOf(contactTransaction)))
        // Act
        subject.onTransactionLongClicked(fctxId)
        // Assert
        verify(mockContactsManager).facilitatedTransactions
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockActivity).showTransactionDeclineDialog(fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun onTransactionLongClickedWaitingForPaymentPrInit() {
        // Arrange
        val fctxId = "FCTX_ID"
        val transaction = FacilitatedTransaction().apply {
            id = fctxId
            role = FacilitatedTransaction.ROLE_PR_INITIATOR
            state = FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT
        }
        val contactTransaction = ContactTransactionModel("", transaction)
        whenever(mockContactsManager.facilitatedTransactions)
                .thenReturn(Observable.fromIterable(listOf(contactTransaction)))
        // Act
        subject.onTransactionLongClicked(fctxId)
        // Assert
        verify(mockContactsManager).facilitatedTransactions
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockActivity).showTransactionCancelDialog(fctxId)
        verifyNoMoreInteractions(mockActivity)
    }

    @Test
    @Throws(Exception::class)
    fun confirmDeclineTransactionShouldShowSuccessful() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contactMdid = "CONTACT_MDID"
        val contact = Contact().apply { mdid = contactMdid }
        whenever(mockContactsManager.getContactFromFctxId(fctxId))
                .thenReturn(Single.just(contact))
        whenever(mockContactsManager.sendPaymentDeclinedResponse(contactMdid, fctxId))
                .thenReturn(Completable.complete())
        subject.contact = contact
        // Act
        subject.confirmDeclineTransaction(fctxId)
        // Assert
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_OK))
        verify(mockActivity).finishPage()
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).getContactFromFctxId(fctxId)
        verify(mockContactsManager).sendPaymentDeclinedResponse(contactMdid, fctxId)
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun confirmDeclineTransactionShouldShowFailure() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contactMdid = "CONTACT_MDID"
        val contact = Contact().apply { mdid = contactMdid }
        whenever(mockContactsManager.getContactFromFctxId(fctxId))
                .thenReturn(Single.just(contact))
        whenever(mockContactsManager.sendPaymentDeclinedResponse(contactMdid, fctxId))
                .thenReturn(Completable.error { Throwable() })
        whenever(mockContactsManager.fetchContacts()).thenReturn(Completable.complete())
        subject.contact = contact
        // Act
        subject.confirmDeclineTransaction(fctxId)
        // Act
        // Assert
        verify(mockActivity, times(2)).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).getContactFromFctxId(fctxId)
        verify(mockContactsManager).sendPaymentDeclinedResponse(contactMdid, fctxId)
        verify(mockContactsManager).fetchContacts()
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun confirmCancelTransactionShouldShowSuccessful() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contactMdid = "CONTACT_MDID"
        val contact = Contact().apply { mdid = contactMdid }
        whenever(mockContactsManager.getContactFromFctxId(fctxId))
                .thenReturn(Single.just(contact))
        whenever(mockContactsManager.sendPaymentCancelledResponse(contactMdid, fctxId))
                .thenReturn(Completable.complete())
        subject.contact = contact
        // Act
        subject.confirmCancelTransaction(fctxId)
        // Assert
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_OK))
        verify(mockActivity).finishPage()
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).getContactFromFctxId(fctxId)
        verify(mockContactsManager).sendPaymentCancelledResponse(contactMdid, fctxId)
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun confirmCancelTransactionShouldShowFailure() {
        // Arrange
        val fctxId = "FCTX_ID"
        val contactMdid = "CONTACT_MDID"
        val contact = Contact().apply { mdid = contactMdid }
        whenever(mockContactsManager.getContactFromFctxId(fctxId))
                .thenReturn(Single.just(contact))
        whenever(mockContactsManager.sendPaymentCancelledResponse(contactMdid, fctxId))
                .thenReturn(Completable.error { Throwable() })
        whenever(mockContactsManager.fetchContacts()).thenReturn(Completable.complete())
        subject.contact = contact
        // Act
        subject.confirmCancelTransaction(fctxId)
        // Act
        // Assert
        verify(mockActivity, times(2)).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).getContactFromFctxId(fctxId)
        verify(mockContactsManager).sendPaymentCancelledResponse(contactMdid, fctxId)
        verify(mockContactsManager).fetchContacts()
        verifyNoMoreInteractions(mockContactsManager)
    }

    @Test
    @Throws(Exception::class)
    fun onAccountChosenShouldShowSuccess() {
        // Arrange
        val fctxId = "FCTX_ID"
        val accountPosition = 0
        val mdid = "MDID"
        val contact = Contact().apply { this.mdid = mdid }
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            intendedAmount = 21 * 1000 * 1000L
        })
        val address = "ADDRESS"
        whenever(mockPayloadDataManager.getNextReceiveAddressAndReserve(eq(accountPosition), any()))
                .thenReturn(Observable.just(address))
        whenever(mockPayloadDataManager.getPositionOfAccountInActiveList(accountPosition))
                .thenReturn(accountPosition)
        whenever(mockContactsManager.sendPaymentRequestResponse(eq(mdid), any<PaymentRequest>(), eq(fctxId)))
                .thenReturn(Completable.complete())
        // Act
        subject.onAccountChosen(accountPosition, fctxId)
        // Assert
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_OK))
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).sendPaymentRequestResponse(eq(mdid), any<PaymentRequest>(), eq(fctxId))
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockPayloadDataManager).getPositionOfAccountInActiveList(accountPosition)
        verify(mockPayloadDataManager).getNextReceiveAddressAndReserve(eq(accountPosition), any())
        verifyNoMoreInteractions(mockPayloadDataManager)
    }

    @Test
    @Throws(Exception::class)
    fun onAccountChosenShouldShowFailure() {
        // Arrange
        val fctxId = "FCTX_ID"
        val accountPosition = 0
        val mdid = "MDID"
        val contact = Contact().apply { this.mdid = mdid }
        subject.contact = contact
        contact.addFacilitatedTransaction(FacilitatedTransaction().apply {
            id = fctxId
            intendedAmount = 21 * 1000 * 1000L
        })
        val address = "ADDRESS"
        whenever(mockPayloadDataManager.getNextReceiveAddressAndReserve(eq(accountPosition), any()))
                .thenReturn(Observable.just(address))
        whenever(mockPayloadDataManager.getPositionOfAccountInActiveList(accountPosition))
                .thenReturn(accountPosition)
        whenever(mockContactsManager.sendPaymentRequestResponse(eq(mdid), any<PaymentRequest>(), eq(fctxId)))
                .thenReturn(Completable.error { Throwable() })
        // Act
        subject.onAccountChosen(accountPosition, fctxId)
        // Assert
        verify(mockActivity).showProgressDialog()
        verify(mockActivity).dismissProgressDialog()
        verify(mockActivity).showToast(any(), eq(ToastCustom.TYPE_ERROR))
        // More interactions as page is set up again, but we're not testing those
        verify(mockContactsManager).sendPaymentRequestResponse(eq(mdid), any<PaymentRequest>(), eq(fctxId))
        verifyNoMoreInteractions(mockContactsManager)
        verify(mockPayloadDataManager).getPositionOfAccountInActiveList(accountPosition)
        verify(mockPayloadDataManager).getNextReceiveAddressAndReserve(eq(accountPosition), any())
        verifyNoMoreInteractions(mockPayloadDataManager)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    @Test
    @Throws(Exception::class)
    fun sortAndUpdateTransactions() {
        // Arrange
        val contactName = "CONTACT_NAME"
        val contact = Contact().apply { name = contactName }
        subject.contact = contact
        val txHash0 = "TX_HASH_0"
        val txHash1 = "TX_HASH_1"
        val fctx0 = FacilitatedTransaction().apply {
            txHash = txHash0
            lastUpdated = 1337L
            intendedAmount = 1337L
            role = FacilitatedTransaction.ROLE_RPR_RECEIVER
        }
        val fctx1 = FacilitatedTransaction().apply {
            txHash = txHash1
            lastUpdated = 1337L
            intendedAmount = 1337L
            role = FacilitatedTransaction.ROLE_PR_INITIATOR
        }
        val fctx2 = FacilitatedTransaction().apply {
            txHash = null
            lastUpdated = 1337L
            intendedAmount = 1337L
        }
        val values = listOf(fctx0, fctx1, fctx2)
        val captor = argumentCaptor<List<JvmType.Object>>()
        // Act
        subject.sortAndUpdateTransactions(values)
        // Assert
        verify(mockActivity).onTransactionsUpdated(captor.capture())
        verifyZeroInteractions(mockActivity)
        val list = captor.firstValue
        (list[0] as ContactTransactionModel).contactName shouldEqual contactName
        (list[1] as TransactionSummary).hash shouldEqual txHash1
        (list[2] as TransactionSummary).hash shouldEqual txHash0
    }

    @Test
    @Throws(Exception::class)
    fun destroy() {
        // Arrange

        // Act
        subject.destroy()
        // Assert
        verify(mockRxBus).unregister(eq(NotificationPayload::class.java), anyOrNull())
    }

    inner class MockApplicationModule(application: Application?) : ApplicationModule(application) {
        override fun providePrefsUtil(): PrefsUtil {
            return mockPrefsUtil
        }

        override fun provideRxBus(): RxBus {
            return mockRxBus
        }
    }

    inner class MockApiModule : ApiModule() {
        override fun provideContactsManager(rxBus: RxBus?): ContactsDataManager {
            return mockContactsManager
        }
    }

    inner class MockDataManagerModule : DataManagerModule() {
        override fun providePayloadDataManager(payloadManager: PayloadManager?, rxBus: RxBus?): PayloadDataManager {
            return mockPayloadDataManager
        }
    }

}
