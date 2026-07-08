package com.tvremote.app.features.iap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.ads.AdsHelper
import com.tvremote.app.analytics.AnalyticsEvents
import com.tvremote.app.databinding.ActivityIapBinding
import com.tvremote.app.features.fullonboard.FullOnboardActivity
import com.tvremote.app.features.iap.utils.BillingClientConnectionListener
import com.tvremote.app.features.iap.utils.DataWrappers
import com.tvremote.app.features.iap.utils.IapConnector
import com.tvremote.app.features.iap.utils.SubscriptionServiceListener
import com.tvremote.app.ui.language.LanguageActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.splash.SplashActivity
import com.tvremote.app.ui.splash.SplashActivity.Companion.isLanguage
import com.tvremote.app.ui.splash.SplashActivity.Companion.isOnboard
import com.tvremote.app.ui.survey.SurveyActivity
import com.tvremote.app.util.FirebaseLogUtils
import com.tvremote.app.util.SpannableTextHelper.setTwoLineStyledText
import com.tvremote.app.util.applyLightSystemBars
import com.tvremote.app.util.hideNavigationBar
import com.tvremote.app.util.invisible
import com.tvremote.app.util.openPrivacyPolicy
import com.tvremote.app.util.openTermsOfUse
import com.tvremote.app.util.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IAPActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIapBinding
    private lateinit var iapConnector: IapConnector
    private val isBillingClientConnected = MutableLiveData(false)
    private val availableProductDetails = mutableMapOf<String, DataWrappers.ProductDetails>()
    private var isTrialOfferAvailable = false
    private var isTrialEnabled = false
    private var trialToggleInitialized = false
    private var loadedProduct: DataWrappers.ProductDetails? = null
    private var isFromProPanel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        applyLightSystemBars()
        hideNavigationBar()

        isFromProPanel = intent.getBooleanExtra(EXTRA_FROM_PRO_PANEL, false)
        val prefs = getSharedPreferences("iap_prefs", MODE_PRIVATE)
        prefs.edit { putInt("iap_view_count", prefs.getInt("iap_view_count", 0) + 1) }
        FirebaseLogUtils.logEvent("iap_view")

        isTrialEnabled = true
        binding.switchFreeTrial.setChecked(true, animate = false)
        binding.switchFreeTrial.setOnCheckedChangeListener { _, checked ->
            isTrialEnabled = checked
            updateTrialPresentation()
        }
        updatePurchaseCardText(getString(R.string.iap_plan_price_placeholder))
        updateTrialPresentation()

        iapConnector = IapManager.getIapConnector(this)
        initInAppListeners()

        lifecycleScope.launch {
            delay(TvRemoteApp.showTimeSubScreenX * 1000)
            binding.closeButton.show()
        }

        binding.btnContinue.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pulse))
        onClickListeners()
    }

    private fun initInAppListeners() {
        iapConnector.addBillingClientConnectionListener(object : BillingClientConnectionListener {
            override fun onConnected(status: Boolean, billingResponseCode: Int) {
                isBillingClientConnected.postValue(status)
            }
        })

        iapConnector.addSubscriptionListener(object : SubscriptionServiceListener {
            override fun onSubscriptionRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                AdsHelper.updateProVersion(purchaseInfo.sku == IapManager.skuKeyWeek)
            }

            override fun onSubscriptionPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                FirebaseLogUtils.logEvent("iap_success")
                val product = availableProductDetails[purchaseInfo.sku]
                AnalyticsEvents.logSubscription(
                    sku = purchaseInfo.sku,
                    revenue = product?.priceAmount ?: 0.0,
                    currency = product?.priceCurrencyCode ?: "USD",
                    isTrial = purchaseInfo.sku.contains("trial", ignoreCase = true) ||
                        product?.billingPeriod == "P3D",
                )
                AdsHelper.updateProVersion(purchaseInfo.sku == IapManager.skuKeyWeek)
                prefs().edit { putInt("iap_purchase_count", prefs().getInt("iap_purchase_count", 0) + 1) }
                startActivity(
                    Intent(this@IAPActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finish()
            }

            override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) {
                iapKeyPrices.values.flatten().firstOrNull()?.let { product ->
                    subscribeUi(product, product.billingPeriod == "P3D")
                } ?: run {
                    binding.textFetchingPrices.text = getString(R.string.iap_fetch_failed)
                    binding.textFetchingPrices.visibility = View.VISIBLE
                    binding.btnContinue.invisible()
                }
            }
        })

        isBillingClientConnected.observe(this) { connected ->
            binding.btnContinue.isEnabled = connected == true
            if (connected == true) {
                binding.btnContinue.setOnClickListener {
                    FirebaseLogUtils.logEvent("btn_continue_clicked_iap")
                    iapConnector.subscribe(this, IapManager.skuKeyWeek)
                }
            }
        }
    }

    private fun subscribeUi(product: DataWrappers.ProductDetails, isTrial: Boolean) {
        loadedProduct = product
        availableProductDetails[product.productId.ifBlank { IapManager.skuKeyWeek }] = product
        isTrialOfferAvailable = isTrial
        if (!trialToggleInitialized) {
            trialToggleInitialized = true
            isTrialEnabled = isTrial
            binding.switchFreeTrial.setCheckedSilently(isTrial, animate = false)
        }
        binding.cardFreeTrial.visibility = if (isTrialOfferAvailable) View.VISIBLE else View.GONE
        binding.btnContinue.show()
        binding.textFetchingPrices.visibility = View.GONE
        updatePurchaseCardText(product.price.orEmpty())
        updateTrialPresentation()
    }

    private fun updatePurchaseCardText(priceText: String) {
        binding.tvEnablePurchase.setTwoLineStyledText(
            topLine = getString(R.string.iap_plan_weekly),
            bottomLine = "${loadedProduct?.priceCurrencyCode ?: "$"} ${loadedProduct?.priceAmount ?: "--"}",
            topColor = ContextCompat.getColor(this, R.color.text_primary),
            bottomColor = ContextCompat.getColor(this, R.color.text_secondary),
        )
    }

    private fun updateTrialPresentation() {
        val priceLabel = loadedProduct?.price?.takeIf { it.isNotBlank() }
            ?: getString(R.string.iap_plan_price_placeholder)
        val trialActive = isTrialEnabled
        binding.tvDisclaimer.text = if (trialActive) {
            getString(R.string.iap_disclaimer_trial_enabled, priceLabel)
        } else {
            getString(R.string.iap_disclaimer_trial_disabled)
        }
        binding.btnContinue.text = if (trialActive) {
            getString(R.string.iap_continue_for_free)
        } else {
            getString(R.string.iap_continue)
        }
        binding.tvNoPaymentNow.text = if (trialActive) {
            getString(R.string.iap_no_payment_now)
        } else {
            getString(R.string.iap_cancel_anytime)
        }
        binding.tvNoPaymentNow.visibility = View.VISIBLE
        binding.tvPurchaseBadge.visibility = View.VISIBLE
        if (trialActive) {
            binding.tvPurchaseBadge.setTwoLineStyledText(
                topLine = getString(R.string.iap_trial_enabled),
                bottomLine = getString(R.string.iap_no_payment_now),
                topColor = ContextCompat.getColor(this, R.color.text_primary),
                bottomColor = ContextCompat.getColor(this, R.color.text_secondary),
            )
        } else {
            binding.tvPurchaseBadge.setTwoLineStyledText(
                topLine = getString(R.string.iap_trial_disabled),
                bottomLine = getString(R.string.iap_badge_cancel_anytime),
                topColor = ContextCompat.getColor(this, R.color.text_primary),
                bottomColor = ContextCompat.getColor(this, R.color.text_secondary),
            )
        }
    }

    private fun onClickListeners() {
        binding.closeButton.setOnClickListener { navigateAfterClose() }
        binding.tvPrivacy.setOnClickListener { openPrivacyPolicy() }
        binding.tvTerms.setOnClickListener { openTermsOfUse() }
    }

    private fun navigateAfterClose() {
        if (isFromProPanel) {
            finish()
            return
        }
        val next = when {
            isOnboard && isLanguage -> Intent(this, LanguageActivity::class.java)
            isOnboard && TvRemoteApp.surveyEnable -> Intent(this, SurveyActivity::class.java)
            isOnboard -> Intent(this, FullOnboardActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        navigateAfterClose()
    }

    private fun prefs() = getSharedPreferences("iap_prefs", MODE_PRIVATE)

    companion object {
        const val EXTRA_FROM_PRO_PANEL = "isFromProPanel"
        var billingMessage = ""
        var billingCode = -1
    }
}
