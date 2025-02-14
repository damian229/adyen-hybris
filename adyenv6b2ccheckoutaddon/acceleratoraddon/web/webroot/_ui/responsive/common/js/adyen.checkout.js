var AdyenCheckoutHybris = (function () {
    'use strict';

    var CardBrand = {
        Visa: 'visa',
        MasterCard: 'mc',
        Elo: 'elo',
        Maestro: 'maestro',
        Electron: 'electron',
        EloDebit: 'elodebit'
    };

    var ErrorMessages = {
        PaymentCancelled: 'checkout.error.authorization.payment.cancelled',
        PaymentError: 'checkout.error.authorization.payment.error',
        PaymentNotAvailable: 'checkout.summary.component.notavailable',
        TermsNotAccepted: 'checkout.error.terms.not.accepted'
    };

    return {
        oneClickForms: [],
        dobDateFormat: "yy-mm-dd",
        securedFieldsData: {},
        allowedCards: [],
        cseEncryptedForm: null,
        checkout: null,
        card: null,
        oneClickCards: {},
        selectedCardBrand: null,
        sepaDirectDebit:null,
        afterPay: null,
        bcmc: null,

        convertCardBrand: function () {
            var cardBrand = this.selectedCardBrand;

            if ( cardBrand === CardBrand.Visa ) {
                return CardBrand.Electron;
            }

            if ( cardBrand === CardBrand.MasterCard ) {
                return CardBrand.Maestro;
            }
            if ( cardBrand === CardBrand.Elo ) {
                return CardBrand.EloDebit;
            }
        },

        isValidBrandType: function () {
            var cardBrand = this.selectedCardBrand;
            return cardBrand === CardBrand.Visa || cardBrand === CardBrand.MasterCard || cardBrand === CardBrand.Elo;
        },

        getCardType: function () {
            var cardType =$( '#adyen_combo_card_type' ).val();
            if ( cardType === "" || cardType == undefined )
                cardType= "credit";
            return cardType;
        },

        isDebitCard: function () {
            return this.getCardType() === 'debit';
        },

        validateForm: function () {
            var paymentMethod = $( 'input[type=radio][name=paymentMethod]:checked' ).val();

            if ( paymentMethod === "" ) {
                window.alert( "Please select a payment method" );
                return false;
            }

            // Check if it is a valid card and encrypt
            if ( paymentMethod === "adyen_cc" ) {
                var isInvalidCard = !this.card.isValid ||
                    (this.isDebitCard() && !this.isValidBrandType());

                if ( isInvalidCard ) {
                    window.alert('Please check your card details.');
                    this.card.showValidation();
                    document.getElementById("card-div").scrollIntoView();
                    return false;
                }
                this.copyCardData();
            }

            if (paymentMethod.indexOf( "adyen_oneclick_" ) === 0) {
                var recurringReference = paymentMethod.slice("adyen_oneclick_".length);
                var oneClickCard = this.oneClickCards[recurringReference];

                if (!(oneClickCard && oneClickCard.isValid)) {
                    window.alert('This credit card is not allowed');
                    return false;
                }
                if ( ['bcmc','maestro'].indexOf(oneClickCard.props.brand) >= 0 ) {
                    this.copyOneClickCardBrandData( recurringReference, oneClickCard.props.brand )
                }
                else {
                    this.copyOneClickCardData( recurringReference, oneClickCard.data.paymentMethod.encryptedSecurityCode, oneClickCard.props.brand );
                }
            }
            $( 'input[name="txvariant"]' ).remove();

            if ( ['eps','ideal'].indexOf(paymentMethod) >= 0 ) {
                var issuerIdField = document.getElementById('issuerId');
                if( issuerIdField.value === "" ) {
                    window.alert("Please select an issuer");
                    return false;
                }
            }

            if ( paymentMethod === "pos" ) {
                var terminalId = $( '#adyen_pos_terminal' );
                if( terminalId.val() === "" ) {
                    window.alert("Please select a terminal");
                    return false;
                }
            }

            if( paymentMethod === "sepadirectdebit" ){
                if( !this.sepaDirectDebit.state.isValid ) {
                    window.alert("Invalid SEPA Owner Name and IBAN number");
                    return false;
                }
            }

            if (paymentMethod === "afterpay_default") {
                if (!this.afterPay.state.isValid) {
                    window.alert("Please fill all the details");
                    this.afterPay.showValidation();
                    document.getElementById("afterpay-container").scrollIntoView();
                    return false;
                }
                var dob = $("input[name=dateOfBirth]").val();
                if ( dob ) {
                    $( "#dob" ).val( dob );
                }
            }

            if (paymentMethod === "paybright") {
                var phoneNumber = $("#p_method_adyen_hpp_paybright_telephonenumber").val();
                if (!phoneNumber) {
                    window.alert("Please fill phone number");
                    document.getElementById("p_method_adyen_hpp_paybright_telephonenumber").scrollIntoView();
                    return false;
                }
            }

            if (paymentMethod === "giftcard") {
                $( 'input[name="giftCardBrand"]' ).val($( 'input[type=radio][name=paymentMethod]:checked' ).attr('brand'));
            }
            
            if (paymentMethod === "bcmc") {
                if (!this.bcmc.state.isValid) {
                    window.alert("Please fill all the details");
                    this.bcmc.showValidation();
                    document.getElementById("bcmc-container").scrollIntoView();
                    return false;
                }
                var state = this.bcmc.data.paymentMethod;
                $( 'input[name="encryptedCardNumber"]' ).val( state.encryptedCardNumber );
                $( 'input[name="encryptedExpiryMonth"]' ).val( state.encryptedExpiryMonth );
                $( 'input[name="encryptedExpiryYear"]' ).val( state.encryptedExpiryYear );
                $( 'input[name="cardHolder"]' ).val( state.holderName );
                $( 'input[name="cardBrand"]' ).val( 'bcmc' );
                $( 'input[name="cardType"]' ).val( 'debit' );
                $( 'input[name="browserInfo"]' ).val( JSON.stringify( this.bcmc.data.browserInfo ) );
            }
                        
            return true;
        },

        copyCardData: function() {
            var state = this.card.data.paymentMethod;
            $( 'input[name="encryptedCardNumber"]' ).val( state.encryptedCardNumber );
            $( 'input[name="encryptedExpiryMonth"]' ).val( state.encryptedExpiryMonth );
            $( 'input[name="encryptedExpiryYear"]' ).val( state.encryptedExpiryYear );
            $( 'input[name="encryptedSecurityCode"]' ).val( state.encryptedSecurityCode );
            $( 'input[name="cardHolder"]' ).val( state.holderName );
            if(this.card.data.storePaymentMethod!=null){
            $( 'input[name="rememberTheseDetails"]' ).val( this.card.data.storePaymentMethod );}

            if ( this.isDebitCard() ) {
                $( 'input[name="cardBrand"]' ).val( this.convertCardBrand() );
                $( 'input[name="cardType"]' ).val( this.getCardType() );
            } else {
                $( 'input[name="cardBrand"]' ).val(this.selectedCardBrand);
            }
                 $( 'input[name="browserInfo"]' ).val( JSON.stringify( this.card.data.browserInfo ) );
        },

        copyOneClickCardData: function ( recurringReference, cvc, brand ) {
            $( "#selectedReference" ).val( recurringReference );
            $( 'input[name="encryptedSecurityCode"]' ).val( cvc );
            $( 'input[name="browserInfo"]' ).val( JSON.stringify( this.card.data.browserInfo ) );
            if (brand) {
                $( 'input[name="cardBrand"]' ).val( brand );
            }

        },
        copyOneClickCardBrandData: function ( recurringReference, brand ) {
            $( "#selectedReference" ).val( recurringReference );
            $( 'input[name="cardBrand"]' ).val( brand );
            $( 'input[name="browserInfo"]' ).val( JSON.stringify( this.card.data.browserInfo ) );
        },

        /**
         * Set Custom values for certain payment methods
         */
        setCustomPaymentMethodValues: function () {
            var paymentMethod = $( 'input[type=radio][name=paymentMethod]:checked' ).val();
            var dob = $( '#p_method_adyen_hpp_' + paymentMethod + '_dob' ).val();
            if ( dob ) {
                $( "#dob" ).val( dob );
            }

            var ssn = $( '#p_method_adyen_hpp_' + paymentMethod + '_ssn' );
            if ( ssn ) {
                $( "#socialSecurityNumber" ).val( ssn.val() );
            }

            var terminalId = $( '#adyen_pos_terminal' );
            if ( terminalId ) {
                $( "#terminalId" ).val( terminalId.val() );
            }

            var firstName = $( '#p_method_adyen_hpp_' + paymentMethod + '_first_name' ).val();
            if (firstName) {
                $( "#firstName" ).val(firstName);
            }

            var lastName = $( '#p_method_adyen_hpp_' + paymentMethod + '_last_name' ).val();
            if (lastName) {
                $( "#lastName" ).val(lastName);
            }
        },

        /**
         * Create DatePicker for Date of Birth field
         *
         * @param element
         */
        createDobDatePicker: function ( element ) {
            $( "." + element ).datepicker( {
                dateFormat: this.dobDateFormat,
                changeMonth: true,
                changeYear: true,
                yearRange: "-120:+0"
            } );
        },


        togglePaymentMethod: function ( paymentMethod ) {
            $( ".payment_method_details" ).hide();
            $( ".chckt-pm__details" ).hide();

            $( "#dd_method_" + paymentMethod ).show();
            $( "#adyen_hpp_" + paymentMethod + "_container" ).show();
        },

        createDfValue: function () {
            window.dfDo( "dfValue" );
        },
        initiateCheckout: function ( locale, environment, clientKey ) {
            var configuration = {
                locale: locale,// shopper's locale
                environment: environment,
                clientKey: clientKey,
                risk: {
                    enabled: false
                }
            };
            this.checkout = new AdyenCheckout( configuration );
        },


    initiateOneClickCard: function(storedCard) {
            var oneClickCardNode = document.getElementById("one-click-card_" + storedCard.storedPaymentMethodId);
            var oneClickCard = this.checkout.create('card', storedCard);
            oneClickCard.mount(oneClickCardNode);
            this.oneClickCards[storedCard.storedPaymentMethodId] = oneClickCard;
        },

        initiateCard: function (allowedCards, showRememberDetails, cardHolderNameRequired) {
            var context = this;
            this.card = this.checkout.create( 'card', {
                type: 'card',
                hasHolderName: true,
                holderNameRequired: cardHolderNameRequired,
                enableStoreDetails: showRememberDetails,
                brands: allowedCards,
                onBrand: copyCardBrand

            });

            function copyCardBrand(event) {
                context.selectedCardBrand = event.brand;
            }

            this.card.mount(document.getElementById('card-div'));
        },

        initiateSepaDirectDebit: function () {
            var context = this;
            var sepaDirectDebitNode = document.getElementById( 'adyen_hpp_sepadirectdebit_container' );
            this.sepaDirectDebit = this.checkout.create( 'sepadirectdebit', {
                onChange: handleOnChange
            } );

            function handleOnChange ( event ) {
                var sepaOwnerNameField = document.getElementById( 'sepaOwnerName' );
                var sepaIbanNumberField = document.getElementById( 'sepaIbanNumber' );

                var sepaOwnerName = event.data.paymentMethod[ "ownerName" ]
                var sepaIbanNumber = event.data.paymentMethod[ "iban" ]

                sepaOwnerNameField.value = sepaOwnerName;
                sepaIbanNumberField.value = sepaIbanNumber;
            }
            this.sepaDirectDebit.mount( sepaDirectDebitNode );
        },


        initiateIdeal: function (idealDetails) {
            var idealNode = document.getElementById('adyen_hpp_ideal_container');
            var ideal = this.checkout.create('ideal', {
                details: idealDetails, // The array of issuers coming from the /paymentMethods api call
                showImage: true, // Optional, defaults to true
                onChange: handleChange // Gets triggered whenever a user selects a bank// Gets triggered once the state is valid
            });

            function handleChange(event) {
                var issuerIdField = document.getElementById('issuerId');
                var issuerId = event.data.paymentMethod.issuer;
                issuerIdField.value = issuerId;
            }

            try {
                ideal.mount(idealNode);
            } catch (e) {
                console.log('Something went wrong trying to mount the iDEAL component: ${e}');
            }
        },


        initiateEps: function (epsDetails) {
            var epsNode = document.getElementById('adyen_hpp_eps_container');
            var eps = this.checkout.create('eps', {
                details: epsDetails, // The array of issuers coming from the /paymentMethods api call
                showImage: true, // Optional, defaults to true
                onChange: handleOnChange // Gets triggered once the shopper selects an issuer
            });

            function handleOnChange(event) {
                var issuerIdField = document.getElementById('issuerId');
                var issuerId = event.data.paymentMethod.issuer;
                issuerIdField.value = issuerId;
            }

            try {
                eps.mount(epsNode);
            } catch (e) {
                console.log('Something went wrong trying to mount the EPS component: ${e}');
            }
        },

        initiatePaypal: function (amount, isImmediateCapture, paypalMerchantId, label) {
            var paypalNode = document.getElementById('adyen-component-button-container-' + label);
            var self = this;

            var adyenComponent = this.checkout.create("paypal", {
                environment: this.checkout.options.environment,
                amount: {
                    currency: amount.currency,
                    value: amount.value
                },
                configuration: {
                    intent: isImmediateCapture ? "capture" : "authorize",
                    merchantId: (this.checkout.options.environment === 'test') ? null : paypalMerchantId,  // Your PayPal Merchant ID. Required for accepting live payments.
                },
                onChange: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                    }
                },
                onSubmit: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                        return false;
                    }
                    self.makePayment(state.data.paymentMethod, component, self.handleResult, label);
                },
                onCancel: function(data, component) {
                    // Sets your prefered status of the component when a PayPal payment is cancelled.
                    self.handleResult(ErrorMessages.PaymentCancelled, true);
                },
                onError: function(error, component) {
                    // Sets your prefered status of the component when an error occurs.
                    self.handleResult(ErrorMessages.PaymentError, true);
                },
                onAdditionalDetails: function(state, component) {
                    self.submitDetails(state.data, self.handleResult);
                }
            });

            try {
                adyenComponent.mount(paypalNode);
            } catch (e) {
                console.log('Something went wrong trying to mount the PayPal component: ' + e);
            }
        },

        initiateApplePay: function (amount, countryCode, applePayMerchantIdentifier, applePayMerchantName, label) {
            var applePayNode = document.getElementById('adyen-component-button-container-' + label);
            var self = this;
            var adyenComponent = this.checkout.create("applepay", {
                amount: {
                    currency: amount.currency,
                    value: amount.value
                },
                countryCode: countryCode,
                configuration: {
                    merchantName: applePayMerchantName,
                    merchantId: applePayMerchantIdentifier
                },
                // Button config
                buttonType: "plain",
                buttonColor: "black",
                onChange: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                    }
                },
                onSubmit: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                        return false;
                    }
                    self.makePayment(state.data.paymentMethod, component, self.handleResult, label);
                },
                onClick: function(resolve, reject) {
                    if (self.isTermsAccepted(label)) {
                        resolve();
                    } else {
                        reject();
                        self.handleResult(ErrorMessages.TermsNotAccepted, true);
                    }
                }
            });

            adyenComponent.isAvailable()
                .then(function() {
                    adyenComponent.mount(applePayNode);
                })
                .catch(function(e) {
                    // Apple Pay is not available
                    console.log('Something went wrong trying to mount the Apple Pay component: ' + e);
                    self.handleResult(ErrorMessages.PaymentNotAvailable, true);
                });
        },
        
        initiateGooglePay: function (amount, merchantAccount, label) {
            var googlePayNode = document.getElementById('adyen-component-button-container-' + label);
            var self = this;
            var adyenComponent = this.checkout.create("paywithgoogle", {
                environment: this.checkout.options.environment,
                amount: {
                    currency: amount.currency,
                    value: amount.value
                },
                configuration: {
                    gatewayMerchantId: merchantAccount,
                    merchantName: merchantAccount
                },
                buttonColor: "white",
                onChange: function(state, component) {
                    if (!state.isValid) {
                        self.hideSpinner();
                    }
                },
                onSubmit: function(state, component) {
                    if (!state.isValid) {
                        self.hideSpinner();
                        return false;
                    }
                    state.showSpinner();
                    state.makePayment(state.data.paymentMethod, component, state.handleResult, label);
                },
                onClick: function(resolve, reject) {
                    if (self.isTermsAccepted(label)) {
                        resolve();
                    } else {
                        reject();
                        self.handleResult(ErrorMessages.TermsNotAccepted, true);
                    }
                }
            });

            adyenComponent.isAvailable()
                .then(function() {
                    adyenComponent.mount(googlePayNode);
                })
                .catch(function(e) {
                    // Google Pay is not available
                    console.log('Something went wrong trying to mount the Google Pay component: ' + e);
                    self.handleResult(ErrorMessages.PaymentNotAvailable, true);
                });
        },
        
        initiateAmazonPay: function (amount, deliveryAddress, amazonPayConfiguration) {
            var label = this.getVisibleLabel();
            var url = new URL(window.location.href);
            var componentConfiguration;
            if(url.searchParams.has('amazonCheckoutSessionId')) {
                // Complete payment, amazon session already created
                componentConfiguration = {
                    amount: amount,
                    amazonCheckoutSessionId: url.searchParams.get('amazonCheckoutSessionId'),
                    showOrderButton: false,
                    onSubmit: (state, component) => {
                        if (!state.isValid) {
                            component.setStatus('ready');
                            this.hideSpinner();
                            return false;
                        }
                        component.setStatus('loading');
                        this.showSpinner();
                        this.makePayment(state.data.paymentMethod, component, this.handleResult, label);
                    }
                };
            } else {
                // Pre-payment, login and choose amazon pay method
                componentConfiguration = {
                    environment: this.checkout.options.environment,
                    addressDetails: {
                        name: deliveryAddress.firstName + ' ' + deliveryAddress.lastName,
                        addressLine1: deliveryAddress.line1,
                        addressLine2: deliveryAddress.line2,
                        city: deliveryAddress.town,
                        postalCode: deliveryAddress.postalCode,
                        countryCode: deliveryAddress.country.isocode,
                        phoneNumber: deliveryAddress.phone
                    },
                    amount: amount,
                    configuration: amazonPayConfiguration,
                    productType: 'PayAndShip',
                    checkoutMode: 'ProcessOrder',
                    returnUrl: window.location.origin + ACC.config.encodedContextPath + '/checkout/multi/adyen/summary/view',
                    onClick: (resolve, reject) => {
                        if (this.isTermsAccepted(label)) {
                            resolve();
                        } else {
                            reject();
                            this.handleResult(ErrorMessages.TermsNotAccepted, true);
                        }
                    }
                };
            }
            
            var amazonPayNode = document.getElementById('adyen-component-button-container-' + label);
            var adyenComponent = this.checkout.create("amazonpay", componentConfiguration);
            
            try {
                adyenComponent.mount(amazonPayNode);
                if(url.searchParams.has('amazonCheckoutSessionId')) {
                    adyenComponent.submit();
                }
            } catch (e) {
                console.log('Something went wrong trying to mount the Amazon Pay component: ' + e);
                this.handleResult(ErrorMessages.PaymentNotAvailable, true);
            }
        },

        initiateMbway: function (label) {
            var mbwayNode = document.getElementById('adyen-component-container-' + label);
            var self = this;

            var adyenComponent = this.checkout.create("mbway", {
                showPayButton: false,
                onChange: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                    }
                },
                onSubmit: function(state, component) {
                    if (!state.isValid) {
                        self.enablePlaceOrder(label);
                        return;
                    }
                    self.makePayment(state.data.paymentMethod, component, self.handleResult, label);
                },
                onAdditionalDetails: function(state, component) {
                    self.submitDetails(state.data, self.handleResult);
                },
                onError: function(error, component) {
                    console.log('Something went wrong trying to make the MBWay payment: ' + error);
                    self.handleResult(ErrorMessages.PaymentError, true);
                }
            });

            try {
                adyenComponent.mount(mbwayNode);
                self.configureButton(adyenComponent, false, label);
            } catch (e) {
                console.log('Something went wrong trying to mount the MBWay component: ' + e);
            }
        },

        initiateAfterPay: function (countryCode) {
            this.afterPay = this.checkout.create("afterpay_default", {
                countryCode: countryCode,
                visibility: { // Optional configuration
                    personalDetails: "editable",
                    billingAddress: "hidden",
                    deliveryAddress: "hidden"
                }
            });

            try {
                this.afterPay.mount('#afterpay-container');
            } catch (e) {
                console.log('Something went wrong trying to mount the afterpay component: ' + e);
            }
        },

        initiatePix: function (label) {
            $("#generateqr-" + label).click(function () {
                AdyenCheckoutHybris.showSpinner();
                if (!AdyenCheckoutHybris.isTermsAccepted(label)) {
                    AdyenCheckoutHybris.handleResult(ErrorMessages.TermsNotAccepted, true)
                } else {
                    $("#generateqr-" + label).hide();
                    $(".checkbox").hide();
                    var actionHandler  = {
                        handleAction : function (action) {
                            AdyenCheckoutHybris.checkout.createFromAction(action, {
                                onAdditionalDetails : function(state) {
                                    AdyenCheckoutHybris.hideSpinner();
                                    AdyenCheckoutHybris.submitDetails(state.data, AdyenCheckoutHybris.handleResult);
                                }
                            }).mount('#qrcode-container-' + label);
                            AdyenCheckoutHybris.hideSpinner();
                        }
                    };
                    AdyenCheckoutHybris.makePayment({ type: "pix" }, actionHandler, AdyenCheckoutHybris.handleResult, label);
                }
            });
        },

        initiateBcmc: function () {
            this.bcmc = this.checkout.create("bcmc", {
                hasHolderName: true,
                holderNameRequired: true
            });

            try {
                this.bcmc.mount('#bcmc-container');
            } catch (e) {
                console.log('Something went wrong trying to mount the BCMC component: ' + e);
            }
        },

        initiateBcmcMobile: function (label) {
            $("#generateqr-" + label).click(function () {
                AdyenCheckoutHybris.showSpinner();
                if (!AdyenCheckoutHybris.isTermsAccepted(label)) {
                    AdyenCheckoutHybris.handleResult(ErrorMessages.TermsNotAccepted, true)
                } else {
                    $("#generateqr-" + label).hide();
                    $(".checkbox").hide();
                    var actionHandler  = {
                        handleAction : function (action) {
                            AdyenCheckoutHybris.checkout.createFromAction(action, {
                                onAdditionalDetails : function(state) {
                                    AdyenCheckoutHybris.hideSpinner();
                                    AdyenCheckoutHybris.submitDetails(state.data, AdyenCheckoutHybris.handleResult);
                                }
                            }).mount('#qrcode-container-' + label);
                            AdyenCheckoutHybris.hideSpinner();
                        }
                    };
                    AdyenCheckoutHybris.makePayment({ type: "bcmc_mobile" }, actionHandler, AdyenCheckoutHybris.handleResult, label);
                }
            });
        },

        configureButton: function (form, useSpinner, label) {
            $(document).ready(function () {
                $("#placeOrder-" + label).click(function () {
                    $(this).prop('disabled', true);
                    if(useSpinner) {
                        AdyenCheckoutHybris.showSpinner();
                    }
                    form.submit();
                });
            });
        },

        makePayment: function (data, component, handleResult, label) {
            $.ajax({
                url: ACC.config.encodedContextPath + '/adyen/component/payment',
                type: "POST",
                data: JSON.stringify({
                            paymentMethodDetails: data,
                            termsCheck: document.getElementById('terms-conditions-check-' + label).checked
                        }),
                contentType: "application/json; charset=utf-8",
                success: function (data) {
                    try {
                        var response = JSON.parse(data);
                        if (response.resultCode && response.resultCode === 'Pending' && response.action) {
                            component.handleAction(response.action);
                        } else if (response.resultCode && (response.resultCode === 'Authorised' || response.resultCode === 'RedirectShopper') ) {
                            handleResult(response, false);
                        } else {
                            handleResult(ErrorMessages.PaymentError, true);
                        }
                    } catch (e) {
                        console.log('Error parsing makePayment response: ' + data);
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                },
                error: function (xmlHttpResponse, exception) {
                    var responseMessage = xmlHttpResponse.responseJSON;
                    if (xmlHttpResponse.status === 400) {
                        handleResult(responseMessage, true);
                    } else {
                        console.log('Error on makePayment: ' + responseMessage);
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                }
            })
        },

        submitDetails: function (data, handleResult) {
            $.ajax({
                url: ACC.config.encodedContextPath + '/adyen/component/submit-details',
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (data) {
                    try {
                        var response = JSON.parse(data);
                        if (response.resultCode) {
                            handleResult(response, false);
                        } else {
                            handleResult(ErrorMessages.PaymentError, true);
                        }
                    } catch (e) {
                        console.log('Error parsing submitDetails response: ' + data);
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                },
                error: function (xhr, exception) {
                    var responseMessage = xhr.responseJSON;
                    if (xhr.status === 400) {
                        handleResult(responseMessage, true);
                    } else {
                        console.log('Error on submitDetails: ' + responseMessage);
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                }
            })
        },

        isTermsAccepted: function(label) {
            return document.getElementById('terms-conditions-check-' + label).checked;
        },

        handleResult: function (data, error) {
            if (error) {
                document.querySelector("#resultData").value = data;
                document.querySelector("#isResultError").value = error;
            } else {
                document.querySelector("#resultData").value = JSON.stringify(data);
            }
            document.querySelector("#handleComponentResultForm").submit();
        },

        showSpinner: function () {
            document.getElementById("spinner_wrapper").style.display = "flex";
        },
        hideSpinner: function () {
            document.getElementById("spinner_wrapper").style.display = "none";
        },
        enablePlaceOrder: function (label) {
            //hide spinner
            this.hideSpinner();
            //enable button
            $("#placeOrder-" + label).prop('disabled', false);
        },
        getVisibleLabel: function () {
            if(!(window.getComputedStyle(document.getElementById('adyen-checkout-visible-xs')).display === "none")) {
                return 'visible-xs';
            }
            if(!(window.getComputedStyle(document.getElementById('adyen-checkout-hidden-xs')).display === "none")) {
                return 'hidden-xs';
            }
            console.log('Something went wrong while trying to compute current visible label');
            return '';
        }
    };
})();
