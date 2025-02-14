/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.factory;

import com.adyen.builders.terminal.TerminalAPIRequestBuilder;
import com.adyen.enums.VatCategory;
import com.adyen.model.AbstractPaymentRequest;
import com.adyen.model.Address;
import com.adyen.model.Amount;
import com.adyen.model.BrowserInfo;
import com.adyen.model.Installments;
import com.adyen.model.Name;
import com.adyen.model.PaymentRequest;
import com.adyen.model.PaymentRequest3d;
import com.adyen.model.additionalData.InvoiceLine;
import com.adyen.model.applicationinfo.ApplicationInfo;
import com.adyen.model.applicationinfo.CommonField;
import com.adyen.model.applicationinfo.ExternalPlatform;
import com.adyen.model.checkout.DefaultPaymentMethodDetails;
import com.adyen.model.checkout.LineItem;
import com.adyen.model.checkout.PaymentMethodDetails;
import com.adyen.model.checkout.PaymentsDetailsRequest;
import com.adyen.model.checkout.PaymentsRequest;
import com.adyen.model.modification.CancelOrRefundRequest;
import com.adyen.model.modification.CaptureRequest;
import com.adyen.model.modification.RefundRequest;
import com.adyen.model.nexo.AmountsReq;
import com.adyen.model.nexo.DocumentQualifierType;
import com.adyen.model.nexo.MessageCategoryType;
import com.adyen.model.nexo.MessageReference;
import com.adyen.model.nexo.PaymentTransaction;
import com.adyen.model.nexo.SaleData;
import com.adyen.model.nexo.TransactionIdentification;
import com.adyen.model.nexo.TransactionStatusRequest;
import com.adyen.model.recurring.DisableRequest;
import com.adyen.model.recurring.Recurring;
import com.adyen.model.recurring.RecurringDetailsRequest;
import com.adyen.model.terminal.SaleToAcquirerData;
import com.adyen.model.terminal.TerminalAPIRequest;
import com.adyen.util.Util;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.model.RequestInfo;
import com.google.gson.Gson;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.util.TaxValue;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.adyen.v6.constants.Adyenv6coreConstants.AFTERPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.CARD_TYPE_DEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.GIFT_CARD;
import static com.adyen.v6.constants.Adyenv6coreConstants.ISSUER_PAYMENT_METHODS;
import static com.adyen.v6.constants.Adyenv6coreConstants.KLARNA;
import static com.adyen.v6.constants.Adyenv6coreConstants.OPENINVOICE_METHODS_API;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYBRIGHT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BOLETO;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BOLETO_SANTANDER;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_FACILPAY_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SEPA_DIRECTDEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_NAME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_VERSION;

public class AdyenRequestFactory {
    private ConfigurationService configurationService;
    private static final Logger LOG = Logger.getLogger(AdyenRequestFactory.class);

    private static final String PLATFORM_NAME = "Hybris";
    private static final String PLATFORM_VERSION_PROPERTY = "build.version.api";
    private static final String IS_3DS2_ALLOWED_PROPERTY = "is3DS2allowed";
    private static final String ALLOW_3DS2_PROPERTY = "allow3DS2";
    private static final String OVERWRITE_BRAND_PROPERTY = "overwriteBrand";

    public PaymentRequest3d create3DAuthorizationRequest(final String merchantAccount, final HttpServletRequest request, final String md, final String paRes) {
        return createBasePaymentRequest(new PaymentRequest3d(), request, merchantAccount).set3DRequestData(md, paRes);
    }

    @Deprecated
    public PaymentRequest createAuthorizationRequest(final String merchantAccount,
                                                     final CartData cartData,
                                                     final HttpServletRequest request,
                                                     final CustomerModel customerModel,
                                                     final RecurringContractMode recurringContractMode) {
        String amount = String.valueOf(cartData.getTotalPrice().getValue());
        String currency = cartData.getTotalPrice().getCurrencyIso();
        String reference = cartData.getCode();

        PaymentRequest paymentRequest = createBasePaymentRequest(new PaymentRequest(), request, merchantAccount).reference(reference).setAmountData(amount, currency);

        // set shopper details
        if (customerModel != null) {
            paymentRequest.setShopperReference(customerModel.getCustomerID());
            paymentRequest.setShopperEmail(customerModel.getContactEmail());
        }

        // set recurring contract
        if (customerModel != null && PAYMENT_METHOD_CC.equals(cartData.getAdyenPaymentMethod())) {
            Recurring recurring = getRecurringContractType(recurringContractMode, cartData.getAdyenRememberTheseDetails());
            paymentRequest.setRecurring(recurring);
        }

        // if address details are provided added it into the request
        if (cartData.getDeliveryAddress() != null) {
            Address deliveryAddress = setAddressData(cartData.getDeliveryAddress());
            paymentRequest.setDeliveryAddress(deliveryAddress);
        }

        if (cartData.getPaymentInfo().getBillingAddress() != null) {
            // set PhoneNumber if it is provided
            if (cartData.getPaymentInfo().getBillingAddress().getPhone() != null && ! cartData.getPaymentInfo().getBillingAddress().getPhone().isEmpty()) {
                paymentRequest.setTelephoneNumber(cartData.getPaymentInfo().getBillingAddress().getPhone());
            }

            Address billingAddress = setAddressData(cartData.getPaymentInfo().getBillingAddress());
            paymentRequest.setBillingAddress(billingAddress);
        }

        // OpenInvoice add required additional data
        if (OPENINVOICE_METHODS_API.contains(cartData.getAdyenPaymentMethod())
                || PAYMENT_METHOD_PAYPAL.contains(cartData.getAdyenPaymentMethod())) {
            paymentRequest.selectedBrand(cartData.getAdyenPaymentMethod());
            setOpenInvoiceData(paymentRequest, cartData, customerModel);

            paymentRequest.setShopperName(getShopperNameFromAddress(cartData.getDeliveryAddress()));
        }
        return paymentRequest;
    }

    public PaymentsDetailsRequest create3DSPaymentsRequest(final Map<String, String> details) {
        PaymentsDetailsRequest paymentsDetailsRequest = new PaymentsDetailsRequest();
        paymentsDetailsRequest.setDetails(details);
        return paymentsDetailsRequest;
    }

    public PaymentsRequest createPaymentsRequest(final String merchantAccount,
                                                 final CartData cartData,
                                                 final RequestInfo requestInfo,
                                                 final CustomerModel customerModel,
                                                 final RecurringContractMode recurringContractMode,
                                                 final Boolean guestUserTokenizationEnabled) {
        PaymentsRequest paymentsRequest = new PaymentsRequest();
        String adyenPaymentMethod = cartData.getAdyenPaymentMethod();

        if (adyenPaymentMethod == null) {
            throw new IllegalArgumentException("Payment method is null");
        }
        //Update payment request for generic information for all payment method types

        updatePaymentRequest(merchantAccount, cartData, requestInfo, customerModel, paymentsRequest);
        Boolean is3DS2allowed = is3DS2Allowed();

        //For credit cards
        if (PAYMENT_METHOD_CC.equals(adyenPaymentMethod) || PAYMENT_METHOD_BCMC.equals(adyenPaymentMethod)) {
            if (CARD_TYPE_DEBIT.equals(cartData.getAdyenCardType())) {
                updatePaymentRequestForDC(paymentsRequest, cartData, recurringContractMode);
            }
            else {
                updatePaymentRequestForCC(paymentsRequest, cartData, recurringContractMode);
            }
            if (is3DS2allowed) {
                paymentsRequest = enhanceForThreeDS2(paymentsRequest, cartData);
            }
            if (customerModel != null && customerModel.getType() == CustomerType.GUEST && guestUserTokenizationEnabled) {
                paymentsRequest.setEnableOneClick(false);
            }
        }
        //For one click
        else if (adyenPaymentMethod.indexOf(PAYMENT_METHOD_ONECLICK) == 0) {
            String selectedReference = cartData.getAdyenSelectedReference();
            if (selectedReference != null && ! selectedReference.isEmpty()) {
                paymentsRequest.addOneClickData(selectedReference, cartData.getAdyenEncryptedSecurityCode());
                String cardBrand = cartData.getAdyenCardBrand();
                if (cardBrand != null) {
                    DefaultPaymentMethodDetails paymentMethodDetails = (DefaultPaymentMethodDetails) (paymentsRequest.getPaymentMethod());
                    paymentMethodDetails.setType(cardBrand);
                    paymentsRequest.setPaymentMethod(paymentMethodDetails);
                }
            }
            if (is3DS2allowed) {
                paymentsRequest = enhanceForThreeDS2(paymentsRequest, cartData);
            }
        }
        //Set Boleto parameters
        else if (cartData.getAdyenPaymentMethod().indexOf(PAYMENT_METHOD_BOLETO) == 0) {
            setBoletoData(paymentsRequest, cartData);
        }
        else if (PAYMENT_METHOD_SEPA_DIRECTDEBIT.equals(cartData.getAdyenPaymentMethod())) {
            setSepaDirectDebitData(paymentsRequest, cartData);
        }

        //For alternate payment methods like iDeal, Paypal etc.
        else {
            updatePaymentRequestForAlternateMethod(paymentsRequest, cartData);
        }

        ApplicationInfo applicationInfo = updateApplicationInfoEcom(paymentsRequest.getApplicationInfo());
        paymentsRequest.setApplicationInfo(applicationInfo);

        paymentsRequest.setReturnUrl(cartData.getAdyenReturnUrl());
        paymentsRequest.setRedirectFromIssuerMethod(RequestMethod.POST.toString());
        paymentsRequest.setRedirectToIssuerMethod(RequestMethod.POST.toString());

        return paymentsRequest;
    }

    public PaymentsRequest createPaymentsRequest(final String merchantAccount,
                                                 final CartData cartData,
                                                 final PaymentMethodDetails paymentMethodDetails,
                                                 final RequestInfo requestInfo,
                                                 final CustomerModel customerModel) {
        PaymentsRequest paymentsRequest = new PaymentsRequest();
        updatePaymentRequest(merchantAccount, cartData, requestInfo, customerModel, paymentsRequest);

        paymentsRequest.setPaymentMethod(paymentMethodDetails);
        paymentsRequest.setReturnUrl(cartData.getAdyenReturnUrl());

        ApplicationInfo applicationInfo = updateApplicationInfoEcom(paymentsRequest.getApplicationInfo());
        paymentsRequest.setApplicationInfo(applicationInfo);

        return paymentsRequest;
    }

    public PaymentsRequest enhanceForThreeDS2(PaymentsRequest paymentsRequest, CartData cartData) {
        if (paymentsRequest.getAdditionalData() == null) {
            paymentsRequest.setAdditionalData(new HashMap<>());
        }
        paymentsRequest.getAdditionalData().put(ALLOW_3DS2_PROPERTY, is3DS2Allowed().toString());
        paymentsRequest.setChannel(PaymentsRequest.ChannelEnum.WEB);
        BrowserInfo browserInfo = new Gson().fromJson(cartData.getAdyenBrowserInfo(), BrowserInfo.class);
        browserInfo = updateBrowserInfoFromRequest(browserInfo, paymentsRequest);
        paymentsRequest.setBrowserInfo(browserInfo);
        return paymentsRequest;
    }

    public BrowserInfo updateBrowserInfoFromRequest(BrowserInfo browserInfo, PaymentsRequest paymentsRequest) {
        if (browserInfo != null) {
            browserInfo.setUserAgent(paymentsRequest.getBrowserInfo().getUserAgent());
            browserInfo.setAcceptHeader(paymentsRequest.getBrowserInfo().getAcceptHeader());
        }
        return browserInfo;
    }

    public ApplicationInfo updateApplicationInfoEcom(ApplicationInfo applicationInfo) {
        updateApplicationInfoPos(applicationInfo);
        CommonField adyenPaymentSource = new CommonField();
        adyenPaymentSource.setName(PLUGIN_NAME);
        adyenPaymentSource.setVersion(PLUGIN_VERSION);
        applicationInfo.setAdyenPaymentSource(adyenPaymentSource);

        return applicationInfo;
    }

    public ApplicationInfo updateApplicationInfoPos(ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            applicationInfo = new ApplicationInfo();
        }
        ExternalPlatform externalPlatform = new ExternalPlatform();
        externalPlatform.setName(PLATFORM_NAME);
        externalPlatform.setVersion(getPlatformVersion());
        applicationInfo.setExternalPlatform(externalPlatform);

        CommonField merchantApplication = new CommonField();
        merchantApplication.setName(PLUGIN_NAME);
        merchantApplication.setVersion(PLUGIN_VERSION);
        applicationInfo.setMerchantApplication(merchantApplication);

        return applicationInfo;
    }

    private void updatePaymentRequest(final String merchantAccount, final CartData cartData, final RequestInfo requestInfo, final CustomerModel customerModel, PaymentsRequest paymentsRequest) {

        //Get details from CartData to set in PaymentRequest.
        String amount = String.valueOf(cartData.getTotalPrice().getValue());
        String currency = cartData.getTotalPrice().getCurrencyIso();
        String reference = cartData.getCode();

        AddressData billingAddress = cartData.getPaymentInfo() != null ? cartData.getPaymentInfo().getBillingAddress() : null;
        AddressData deliveryAddress = cartData.getDeliveryAddress();

        //Get details from HttpServletRequest to set in PaymentRequest.
        String userAgent = requestInfo.getUserAgent();
        String acceptHeader = requestInfo.getAcceptHeader();
        String shopperIP = requestInfo.getShopperIp();
        String origin = requestInfo.getOrigin();
        String shopperLocale = requestInfo.getShopperLocale();

        paymentsRequest.setAmountData(amount, currency)
                .reference(reference)
                .merchantAccount(merchantAccount)
                .addBrowserInfoData(userAgent, acceptHeader)
                .shopperIP(shopperIP)
                .origin(origin)
                .shopperLocale(shopperLocale)
                .setCountryCode(getCountryCode(cartData));

        // set shopper details from CustomerModel.
        if (customerModel != null) {
            paymentsRequest.setShopperReference(customerModel.getCustomerID());
            paymentsRequest.setShopperEmail(customerModel.getContactEmail());
        }

        // if address details are provided, set it to the PaymentRequest
        if (deliveryAddress != null) {
            paymentsRequest.setDeliveryAddress(setAddressData(deliveryAddress));
        }

        if (billingAddress != null) {
            paymentsRequest.setBillingAddress(setAddressData(billingAddress));
            // set PhoneNumber if it is provided
            String phone = billingAddress.getPhone();
            if (phone != null && ! phone.isEmpty()) {
                paymentsRequest.setTelephoneNumber(phone);
            }
        }

        if (PAYMENT_METHOD_PIX.equals(cartData.getAdyenPaymentMethod())) {
            setPixData(paymentsRequest, cartData);
        }

    }

    private void updatePaymentRequestForCC(PaymentsRequest paymentsRequest, CartData cartData, RecurringContractMode recurringContractMode) {
        Recurring recurringContract = getRecurringContractType(recurringContractMode);
        Recurring.ContractEnum contractEnum = null;
        if (recurringContract != null) {
            contractEnum = recurringContract.getContract();
        }

        paymentsRequest.setEnableRecurring(false);
        paymentsRequest.setEnableOneClick(false);

        String encryptedCardNumber = cartData.getAdyenEncryptedCardNumber();
        String encryptedExpiryMonth = cartData.getAdyenEncryptedExpiryMonth();
        String encryptedExpiryYear = cartData.getAdyenEncryptedExpiryYear();
        if (cartData.getAdyenInstallments() != null) {
            Installments installmentObj = new Installments();
            installmentObj.setValue(cartData.getAdyenInstallments());
            paymentsRequest.setInstallments(installmentObj);
        }

        if (! StringUtils.isEmpty(encryptedCardNumber) && ! StringUtils.isEmpty(encryptedExpiryMonth) && ! StringUtils.isEmpty(encryptedExpiryYear)) {

            paymentsRequest.addEncryptedCardData(encryptedCardNumber, encryptedExpiryMonth, encryptedExpiryYear, cartData.getAdyenEncryptedSecurityCode(), cartData.getAdyenCardHolder());
        }
        if (Recurring.ContractEnum.ONECLICK_RECURRING == contractEnum) {
            paymentsRequest.setEnableRecurring(true);
            if(cartData.getAdyenRememberTheseDetails()) {
                paymentsRequest.setEnableOneClick(true);
            }
        } else if (Recurring.ContractEnum.ONECLICK == contractEnum && cartData.getAdyenRememberTheseDetails() ) {
            paymentsRequest.setEnableOneClick(true);
        } else if (Recurring.ContractEnum.RECURRING == contractEnum) {
            paymentsRequest.setEnableRecurring(true);
        }

        // Set storeDetails parameter when shopper selected to have his card details stored
        if (cartData.getAdyenRememberTheseDetails()) {
            DefaultPaymentMethodDetails paymentMethodDetails = (DefaultPaymentMethodDetails) paymentsRequest.getPaymentMethod();
            paymentMethodDetails.setStoreDetails(true);
        }

        // For Dual branded card set card brand as payment method type
        if (!StringUtils.isEmpty(cartData.getAdyenCardBrand())) {
            paymentsRequest.getPaymentMethod().setType(cartData.getAdyenCardBrand());
        }
    }

    private void updatePaymentRequestForDC(PaymentsRequest paymentsRequest, CartData cartData, RecurringContractMode recurringContractMode) {

        Recurring recurringContract = getRecurringContractType(recurringContractMode);
        Recurring.ContractEnum contractEnum = null;
        if (recurringContract != null) {
            contractEnum = recurringContract.getContract();
        }

        paymentsRequest.setEnableRecurring(false);
        paymentsRequest.setEnableOneClick(false);

        String encryptedCardNumber = cartData.getAdyenEncryptedCardNumber();
        String encryptedExpiryMonth = cartData.getAdyenEncryptedExpiryMonth();
        String encryptedExpiryYear = cartData.getAdyenEncryptedExpiryYear();
        if ((Recurring.ContractEnum.ONECLICK_RECURRING == contractEnum || Recurring.ContractEnum.ONECLICK == contractEnum) && cartData.getAdyenRememberTheseDetails()) {
            paymentsRequest.setEnableOneClick(true);
        }
        if (! StringUtils.isEmpty(encryptedCardNumber) && ! StringUtils.isEmpty(encryptedExpiryMonth) && ! StringUtils.isEmpty(encryptedExpiryYear)) {
            paymentsRequest.addEncryptedCardData(encryptedCardNumber, encryptedExpiryMonth, encryptedExpiryYear, cartData.getAdyenEncryptedSecurityCode(), cartData.getAdyenCardHolder());
        }

        // Set storeDetails parameter when shopper selected to have his card details stored
        if (cartData.getAdyenRememberTheseDetails()) {
            DefaultPaymentMethodDetails paymentMethodDetails = (DefaultPaymentMethodDetails) paymentsRequest.getPaymentMethod();
            paymentMethodDetails.setStoreDetails(true);
        }

        String cardBrand = cartData.getAdyenCardBrand();
        paymentsRequest.putAdditionalDataItem(OVERWRITE_BRAND_PROPERTY, "true");
        paymentsRequest.getPaymentMethod().setType(cardBrand);
    }

    private void updatePaymentRequestForAlternateMethod(PaymentsRequest paymentsRequest, CartData cartData) {
        String adyenPaymentMethod = cartData.getAdyenPaymentMethod();
        DefaultPaymentMethodDetails paymentMethod = new DefaultPaymentMethodDetails();
        paymentsRequest.setPaymentMethod(paymentMethod);
        paymentMethod.setType(adyenPaymentMethod);
        paymentsRequest.setReturnUrl(cartData.getAdyenReturnUrl());
        if (ISSUER_PAYMENT_METHODS.contains(adyenPaymentMethod)) {
            paymentMethod.setIssuer(cartData.getAdyenIssuerId());
        } else if (adyenPaymentMethod.startsWith(KLARNA)
                || adyenPaymentMethod.startsWith(PAYMENT_METHOD_FACILPAY_PREFIX)
                || OPENINVOICE_METHODS_API.contains(adyenPaymentMethod)) {
            setOpenInvoiceData(paymentsRequest, cartData);
        } else if (adyenPaymentMethod.equals(PAYMENT_METHOD_PAYPAL) && cartData.getDeliveryAddress() != null) {
            Name shopperName = getShopperNameFromAddress(cartData.getDeliveryAddress());
            paymentsRequest.setShopperName(shopperName);
        } else if (adyenPaymentMethod.equals(GIFT_CARD)) {
            paymentMethod.setBrand(cartData.getAdyenGiftCardBrand());
        }
    }

    private String getCountryCode(CartData cartData) {
        //Identify country code based on shopper's delivery address
        String countryCode = "";
        AddressData billingAddressData = cartData.getPaymentInfo() != null ? cartData.getPaymentInfo().getBillingAddress() : null;
        if (billingAddressData != null) {
            CountryData billingCountry = billingAddressData.getCountry();
            if (billingCountry != null) {
                countryCode = billingCountry.getIsocode();
            }
        } else {
            AddressData deliveryAddressData = cartData.getDeliveryAddress();
            if (deliveryAddressData != null) {
                CountryData deliveryCountry = deliveryAddressData.getCountry();
                if (deliveryCountry != null) {
                    countryCode = deliveryCountry.getIsocode();
                }
            }
        }
        return countryCode;
    }


    public CaptureRequest createCaptureRequest(final String merchantAccount, final BigDecimal amount, final Currency currency, final String authReference, final String merchantReference) {
        CaptureRequest request = new CaptureRequest().fillAmount(String.valueOf(amount), currency.getCurrencyCode())
                                                     .merchantAccount(merchantAccount)
                                                     .originalReference(authReference)
                                                     .reference(merchantReference);
        updateApplicationInfoEcom(request.getApplicationInfo());
        return request;
    }

    public CancelOrRefundRequest createCancelOrRefundRequest(final String merchantAccount, final String authReference, final String merchantReference) {
        CancelOrRefundRequest request = new CancelOrRefundRequest().merchantAccount(merchantAccount).originalReference(authReference).reference(merchantReference);
        updateApplicationInfoEcom(request.getApplicationInfo());
        return request;
    }

    public RefundRequest createRefundRequest(final String merchantAccount, final BigDecimal amount, final Currency currency, final String authReference, final String merchantReference) {
        RefundRequest request = new RefundRequest().fillAmount(String.valueOf(amount), currency.getCurrencyCode())
                                                   .merchantAccount(merchantAccount)
                                                   .originalReference(authReference)
                                                   .reference(merchantReference);
        updateApplicationInfoEcom(request.getApplicationInfo());
        return request;
    }

    public RecurringDetailsRequest createListRecurringDetailsRequest(final String merchantAccount, final String customerId) {
        return new RecurringDetailsRequest().merchantAccount(merchantAccount).shopperReference(customerId).selectOneClickContract();
    }

    /**
     * Creates a request to disable a recurring contract
     */
    public DisableRequest createDisableRequest(final String merchantAccount, final String customerId, final String recurringReference) {
        return new DisableRequest().merchantAccount(merchantAccount).shopperReference(customerId).recurringDetailReference(recurringReference);
    }

    private <T extends AbstractPaymentRequest> T createBasePaymentRequest(T abstractPaymentRequest, HttpServletRequest request, final String merchantAccount) {
        String userAgent = request.getHeader("User-Agent");
        String acceptHeader = request.getHeader("Accept");
        String shopperIP = request.getRemoteAddr();
        abstractPaymentRequest.merchantAccount(merchantAccount).setBrowserInfoData(userAgent, acceptHeader).shopperIP(shopperIP);

        return abstractPaymentRequest;
    }


    public TerminalAPIRequest createTerminalAPIRequestForStatus(final CartData cartData, String originalServiceId) {
        TransactionStatusRequest transactionStatusRequest = new TransactionStatusRequest();
        transactionStatusRequest.setReceiptReprintFlag(true);

        MessageReference messageReference = new MessageReference();
        messageReference.setMessageCategory(MessageCategoryType.PAYMENT);
        messageReference.setSaleID(cartData.getStore());
        messageReference.setServiceID(originalServiceId);

        transactionStatusRequest.setMessageReference(messageReference);
        transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CASHIER_RECEIPT);
        transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CUSTOMER_RECEIPT);

        String serviceId = Long.toString(System.currentTimeMillis() % 10000000000L);

        TerminalAPIRequestBuilder builder = new TerminalAPIRequestBuilder(cartData.getStore(), serviceId, cartData.getAdyenTerminalId());
        builder.withTransactionStatusRequest(transactionStatusRequest);

        return builder.build();
    }

    public TerminalAPIRequest createTerminalAPIRequest(final CartData cartData, CustomerModel customer, RecurringContractMode recurringContractMode, String serviceId) throws Exception {
        com.adyen.model.nexo.PaymentRequest paymentRequest = new com.adyen.model.nexo.PaymentRequest();

        SaleData saleData = new SaleData();
        TransactionIdentification transactionIdentification = new TransactionIdentification();
        transactionIdentification.setTransactionID(cartData.getCode());
        XMLGregorianCalendar timestamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
        transactionIdentification.setTimeStamp(timestamp);
        saleData.setSaleTransactionID(transactionIdentification);

        //Set recurring contract, if exists
        if(customer != null) {
            String shopperReference = customer.getCustomerID();
            String shopperEmail = customer.getContactEmail();
            Recurring recurringContract = getRecurringContractType(recurringContractMode);
            SaleToAcquirerData saleToAcquirerData = new SaleToAcquirerData();

            if (recurringContract != null && StringUtils.isNotEmpty(shopperReference) && StringUtils.isNotEmpty(shopperEmail)) {
                saleToAcquirerData.setShopperEmail(shopperEmail);
                saleToAcquirerData.setShopperReference(shopperReference);
                saleToAcquirerData.setRecurringContract(recurringContract.getContract().toString());
            }
            updateApplicationInfoPos(saleToAcquirerData.getApplicationInfo());
            saleData.setSaleToAcquirerData(saleToAcquirerData);
        }

        paymentRequest.setSaleData(saleData);

        PaymentTransaction paymentTransaction = new PaymentTransaction();
        AmountsReq amountsReq = new AmountsReq();
        amountsReq.setCurrency(cartData.getTotalPrice().getCurrencyIso());
        amountsReq.setRequestedAmount(cartData.getTotalPrice().getValue());
        paymentTransaction.setAmountsReq(amountsReq);

        paymentRequest.setPaymentTransaction(paymentTransaction);

        TerminalAPIRequestBuilder builder = new TerminalAPIRequestBuilder(cartData.getStore(), serviceId, cartData.getAdyenTerminalId());
        builder.withPaymentRequest(paymentRequest);

        return builder.build();
    }

    /**
     * Set Address Data into API
     */
    private Address setAddressData(AddressData addressData) {

        Address address = new Address();

        // set defaults because all fields are required into the API
        address.setCity("NA");
        address.setCountry("NA");
        address.setHouseNumberOrName("NA");
        address.setPostalCode("NA");
        address.setStateOrProvince("NA");
        address.setStreet("NA");

        // set the actual values if they are available
        if (addressData.getTown() != null && ! addressData.getTown().isEmpty()) {
            address.setCity(addressData.getTown());
        }

        if (addressData.getCountry() != null && ! addressData.getCountry().getIsocode().isEmpty()) {
            address.setCountry(addressData.getCountry().getIsocode());
        }

        if (addressData.getLine1() != null && ! addressData.getLine1().isEmpty()) {
            address.setStreet(addressData.getLine1());
        }

        if (addressData.getLine2() != null && ! addressData.getLine2().isEmpty()) {
            address.setHouseNumberOrName(addressData.getLine2());
        }

        if (addressData.getPostalCode() != null && ! address.getPostalCode().isEmpty()) {
            address.setPostalCode(addressData.getPostalCode());
        }

        //State value will be updated later for boleto in boleto specific method.
        if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocodeShort())) {
            address.setStateOrProvince(addressData.getRegion().getIsocodeShort());
        } else if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocode())) {
            address.setStateOrProvince(addressData.getRegion().getIsocode());
        }

        return address;
    }


    /**
     * Return Recurring object from RecurringContractMode
     */
    private Recurring getRecurringContractType(RecurringContractMode recurringContractMode) {
        Recurring recurringContract = new Recurring();

        //If recurring contract is disabled, return null
        if (recurringContractMode == null || RecurringContractMode.NONE.equals(recurringContractMode)) {
            return null;
        }

        String recurringMode = recurringContractMode.getCode();
        Recurring.ContractEnum contractEnum = Recurring.ContractEnum.valueOf(recurringMode);

        recurringContract.contract(contractEnum);

        return recurringContract;
    }

    /**
     * Return the recurringContract. If the user did not want to save the card don't send it as ONECLICK
     */
    private Recurring getRecurringContractType(RecurringContractMode recurringContractMode, final Boolean enableOneClick) {
        Recurring recurringContract = getRecurringContractType(recurringContractMode);

        //If recurring contract is disabled, return null
        if (recurringContract == null) {
            return null;
        }

        // if user want to save his card use the configured recurring contract type
        if (enableOneClick != null && enableOneClick) {
            return recurringContract;
        }

        Recurring.ContractEnum contractEnum = recurringContract.getContract();
        /*
         * If save card is not checked do the folllowing changes:
         * NONE => NONE
         * ONECLICK => NONE
         * ONECLICK,RECURRING => RECURRING
         * RECURRING => RECURRING
         */
        if (Recurring.ContractEnum.ONECLICK_RECURRING.equals(contractEnum) || Recurring.ContractEnum.RECURRING.equals(contractEnum)) {
            return recurringContract.contract(Recurring.ContractEnum.RECURRING);
        }

        return null;
    }

    /**
     * Get shopper name and gender
     */
    private Name getShopperNameFromAddress(AddressData addressData) {
        Name shopperName = new Name();

        shopperName.setFirstName(addressData.getFirstName());
        shopperName.setLastName(addressData.getLastName());
        shopperName.setGender(Name.GenderEnum.UNKNOWN);

        if (addressData.getTitleCode() != null && ! addressData.getTitleCode().isEmpty()) {
            if (addressData.getTitleCode().equals("mrs") || addressData.getTitleCode().equals("miss") || addressData.getTitleCode().equals("ms")) {
                shopperName.setGender(Name.GenderEnum.FEMALE);
            } else {
                shopperName.setGender(Name.GenderEnum.MALE);
            }
        }

        return shopperName;
    }


    /**
     * Set the required fields for using the OpenInvoice API
     * <p>
     * To deprecate when RatePay is natively implemented
     */
    public void setOpenInvoiceData(PaymentRequest paymentRequest, CartData cartData, final CustomerModel customerModel) {
        // set date of birth
        if (cartData.getAdyenDob() != null) {
            paymentRequest.setDateOfBirth(cartData.getAdyenDob());
        }

        if (cartData.getAdyenSocialSecurityNumber() != null && ! cartData.getAdyenSocialSecurityNumber().isEmpty()) {
            paymentRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        }

        if (cartData.getAdyenDfValue() != null && ! cartData.getAdyenDfValue().isEmpty()) {
            paymentRequest.setDeviceFingerprint(cartData.getAdyenDfValue());
        }

        // set the invoice lines
        List<InvoiceLine> invoiceLines = new ArrayList();
        String currency = cartData.getTotalPrice().getCurrencyIso();

        for (OrderEntryData entry : cartData.getEntries()) {

            // Use totalPrice because the basePrice does include tax as well if you have configured this to be calculated in the price
            BigDecimal pricePerItem = entry.getTotalPrice().getValue().divide(new BigDecimal(entry.getQuantity()));


            String description = "NA";
            if (entry.getProduct().getName() != null && ! entry.getProduct().getName().equals("")) {
                description = entry.getProduct().getName();
            }

            // Tax of total price (included quantity)
            Double tax = entry.getTaxValues().stream().map(taxValue -> taxValue.getAppliedValue()).reduce(0.0, (x, y) -> x = x + y);


            // Calculate Tax per quantitiy
            if (tax > 0) {
                tax = tax / entry.getQuantity().intValue();
            }

            // Calculate price without tax
            Amount itemAmountWithoutTax = Util.createAmount(pricePerItem.subtract(new BigDecimal(tax)), currency);
            Double percentage = entry.getTaxValues().stream().map(taxValue -> taxValue.getValue()).reduce(0.0, (x, y) -> x = x + y) * 100;

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setCurrencyCode(currency);
            invoiceLine.setDescription(description);

            /*
             * The price for one item in the invoice line, represented in minor units.
             * The due amount for the item, VAT excluded.
             */
            invoiceLine.setItemAmount(itemAmountWithoutTax.getValue());

            // The VAT due for one item in the invoice line, represented in minor units.
            invoiceLine.setItemVATAmount(Util.createAmount(BigDecimal.valueOf(tax), currency).getValue());

            // The VAT percentage for one item in the invoice line, represented in minor units.
            invoiceLine.setItemVatPercentage(percentage.longValue());

            // The country-specific VAT category a product falls under.  Allowed values: (High,Low,None)
            invoiceLine.setVatCategory(VatCategory.NONE);

            // An unique id for this item. Required for RatePay if the description of each item is not unique.
            if (! entry.getProduct().getCode().isEmpty()) {
                invoiceLine.setItemId(entry.getProduct().getCode());
            }

            invoiceLine.setNumberOfItems(entry.getQuantity().intValue());

            if (entry.getProduct() != null && ! entry.getProduct().getCode().isEmpty()) {
                invoiceLine.setItemId(entry.getProduct().getCode());
            }

            if (entry.getProduct() != null && ! entry.getProduct().getCode().isEmpty()) {
                invoiceLine.setItemId(entry.getProduct().getCode());
            }

            LOG.debug("InvoiceLine Product:" + invoiceLine.toString());
            invoiceLines.add(invoiceLine);

        }

        // Add delivery costs
        if (cartData.getDeliveryCost() != null) {

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setCurrencyCode(currency);
            invoiceLine.setDescription("Delivery Costs");
            Amount deliveryAmount = Util.createAmount(cartData.getDeliveryCost().getValue().toString(), currency);
            invoiceLine.setItemAmount(deliveryAmount.getValue());
            invoiceLine.setItemVATAmount(new Long("0"));
            invoiceLine.setItemVatPercentage(new Long("0"));
            invoiceLine.setVatCategory(VatCategory.NONE);
            invoiceLine.setNumberOfItems(1);
            LOG.debug("InvoiceLine DeliveryCosts:" + invoiceLine.toString());
            invoiceLines.add(invoiceLine);
        }

        paymentRequest.setInvoiceLines(invoiceLines);
    }


    /*
     * Set the required fields for using the OpenInvoice API
     */
    public void setOpenInvoiceData(PaymentsRequest paymentsRequest, CartData cartData) {
        paymentsRequest.setShopperName(getShopperNameFromAddress(cartData.getDeliveryAddress()));

        // set date of birth
        if (cartData.getAdyenDob() != null) {
            paymentsRequest.setDateOfBirth(cartData.getAdyenDob());
        }

        if (cartData.getAdyenSocialSecurityNumber() != null && ! cartData.getAdyenSocialSecurityNumber().isEmpty()) {
            paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        }

        if (cartData.getAdyenDfValue() != null && ! cartData.getAdyenDfValue().isEmpty()) {
            paymentsRequest.setDeviceFingerprint(cartData.getAdyenDfValue());
        }

        if (AFTERPAY.equals(cartData.getAdyenPaymentMethod())) {
            paymentsRequest.setShopperEmail(cartData.getAdyenShopperEmail());
            paymentsRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
            paymentsRequest.setShopperName(getAfterPayShopperName(cartData));
        } else if (PAYBRIGHT.equals(cartData.getAdyenPaymentMethod())) {
            paymentsRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
        }

        // set the invoice lines
        List<LineItem> invoiceLines = new ArrayList<>();
        String currency = cartData.getTotalPrice().getCurrencyIso();

        for (OrderEntryData entry : cartData.getEntries()) {
            if (entry.getQuantity() == 0L) {
                // skip zero quantities
                continue;
            }
            // Use totalPrice because the basePrice does include tax as well if you have configured this to be calculated in the price
            BigDecimal pricePerItem = entry.getTotalPrice().getValue().divide(new BigDecimal(entry.getQuantity()));

            String description = "NA";
            if (entry.getProduct().getName() != null && ! entry.getProduct().getName().isEmpty()) {
                description = entry.getProduct().getName();
            }

            // Tax of total price (included quantity)
            Double tax = entry.getTaxValues().stream().map(TaxValue::getAppliedValue).reduce(0.0, (x, y) -> x + y);

            // Calculate Tax per quantitiy
            if (tax > 0) {
                tax = tax / entry.getQuantity().intValue();
            }

            // Calculate price without tax
            Amount itemAmountWithoutTax = Util.createAmount(pricePerItem.subtract(new BigDecimal(tax)), currency);
            Double percentage = entry.getTaxValues().stream().map(TaxValue::getValue).reduce(0.0, (x, y) -> x + y) * 100;

            LineItem invoiceLine = new LineItem();
            invoiceLine.setDescription(description);

            /*
             * The price for one item in the invoice line, represented in minor units.
             * The due amount for the item, VAT excluded.
             */
            invoiceLine.setAmountExcludingTax(itemAmountWithoutTax.getValue());

            // The VAT due for one item in the invoice line, represented in minor units.
            invoiceLine.setTaxAmount(Util.createAmount(BigDecimal.valueOf(tax), currency).getValue());

            // The VAT percentage for one item in the invoice line, represented in minor units.
            invoiceLine.setTaxPercentage(percentage.longValue());

            // The country-specific VAT category a product falls under.  Allowed values: (High,Low,None)
            invoiceLine.setTaxCategory(LineItem.TaxCategoryEnum.NONE);

            invoiceLine.setQuantity(entry.getQuantity());

            if (entry.getProduct() != null && ! entry.getProduct().getCode().isEmpty()) {
                invoiceLine.setId(entry.getProduct().getCode());
            }

            LOG.debug("InvoiceLine Product:" + invoiceLine.toString());
            invoiceLines.add(invoiceLine);
        }

        // Add delivery costs
        if (cartData.getDeliveryCost() != null) {
            LineItem invoiceLine = new LineItem();
            invoiceLine.setDescription("Delivery Costs");
            Amount deliveryAmount = Util.createAmount(cartData.getDeliveryCost().getValue().toString(), currency);
            invoiceLine.setAmountExcludingTax(deliveryAmount.getValue());
            invoiceLine.setTaxAmount(new Long("0"));
            invoiceLine.setTaxPercentage(new Long("0"));
            invoiceLine.setTaxCategory(LineItem.TaxCategoryEnum.NONE);
            invoiceLine.setQuantity(1L);
            LOG.debug("InvoiceLine DeliveryCosts:" + invoiceLine.toString());
            invoiceLines.add(invoiceLine);
        }

        paymentsRequest.setLineItems(invoiceLines);
    }

    private Name getAfterPayShopperName(CartData cartData) {
        Name name = new Name();
        name.setFirstName(cartData.getAdyenFirstName());
        name.setLastName(cartData.getAdyenLastName());
        name.gender(Name.GenderEnum.valueOf(cartData.getAdyenShopperGender()));
        return name;
    }

    /**
     * Set Boleto payment request data
     */
    private void setBoletoData(PaymentsRequest paymentsRequest, CartData cartData) {
        DefaultPaymentMethodDetails paymentMethodDetails = (DefaultPaymentMethodDetails) (paymentsRequest.getPaymentMethod());
        if (paymentMethodDetails == null) {
            paymentMethodDetails = new DefaultPaymentMethodDetails();
        }
        paymentMethodDetails.setType(PAYMENT_METHOD_BOLETO_SANTANDER);
        paymentsRequest.setPaymentMethod(paymentMethodDetails);
        paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());

        Name shopperName = new Name();
        shopperName.setFirstName(cartData.getAdyenFirstName());
        shopperName.setLastName(cartData.getAdyenLastName());
        paymentsRequest.setShopperName(shopperName);

        if (paymentsRequest.getBillingAddress() != null) {
            String stateOrProvinceBilling = paymentsRequest.getBillingAddress().getStateOrProvince();
            if (! StringUtils.isEmpty(stateOrProvinceBilling) && stateOrProvinceBilling.length() > 2) {
                String shortStateOrProvince = stateOrProvinceBilling.substring(stateOrProvinceBilling.length() - 2);
                paymentsRequest.getBillingAddress().setStateOrProvince(shortStateOrProvince);
            }
        }
        if (paymentsRequest.getDeliveryAddress() != null) {
            String stateOrProvinceDelivery = paymentsRequest.getDeliveryAddress().getStateOrProvince();
            if (! StringUtils.isEmpty(stateOrProvinceDelivery) && stateOrProvinceDelivery.length() > 2) {
                String shortStateOrProvince = stateOrProvinceDelivery.substring(stateOrProvinceDelivery.length() - 2);
                paymentsRequest.getDeliveryAddress().setStateOrProvince(shortStateOrProvince);
            }
        }
    }

    private void setSepaDirectDebitData(PaymentsRequest paymentRequest, CartData cartData) {
        DefaultPaymentMethodDetails paymentMethodDetails = new DefaultPaymentMethodDetails();
        paymentMethodDetails.setSepaOwnerName(cartData.getAdyenSepaOwnerName());
        paymentMethodDetails.setSepaIbanNumber(cartData.getAdyenSepaIbanNumber());
        paymentMethodDetails.setType(PAYMENT_METHOD_SEPA_DIRECTDEBIT);
        paymentRequest.setPaymentMethod(paymentMethodDetails);
    }

    private void setPixData(PaymentsRequest paymentsRequest, CartData cartData) {
        Name shopperName = new Name();
        shopperName.setFirstName(cartData.getAdyenFirstName());
        shopperName.setLastName(cartData.getAdyenLastName());
        paymentsRequest.setShopperName(shopperName);
        paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());

        List<LineItem> invoiceLines = new ArrayList<>();
        for (OrderEntryData entry : cartData.getEntries()) {
            if (entry.getQuantity() == 0L) {
                // skip zero quantities
                continue;
            }

            BigDecimal productAmountIncludingTax = entry.getBasePrice().getValue();
            String productName = "NA";
            if (entry.getProduct().getName() != null && !entry.getProduct().getName().isEmpty()) {
                productName = entry.getProduct().getName();
            }
            LineItem lineItem = new LineItem();
            lineItem.setAmountIncludingTax(productAmountIncludingTax.longValue());
            lineItem.setId(productName);
            invoiceLines.add(lineItem);
        }
        paymentsRequest.setLineItems(invoiceLines);
    }

    private String getPlatformVersion() {
        return getConfigurationService().getConfiguration().getString(PLATFORM_VERSION_PROPERTY);
    }

    private Boolean is3DS2Allowed() {

        Configuration configuration = getConfigurationService().getConfiguration();
        boolean is3DS2AllowedValue = false;
        if (configuration.containsKey(IS_3DS2_ALLOWED_PROPERTY)) {
            is3DS2AllowedValue = configuration.getBoolean(IS_3DS2_ALLOWED_PROPERTY);
        }
        return is3DS2AllowedValue;
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
