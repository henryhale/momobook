package com.momobook.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryStyle(val icon: ImageVector, val color: Color)

object CategoryStyles {
    private val map = mapOf(
        "Data & Airtime" to CategoryStyle(Icons.Rounded.Wifi, Color(0xFF7A5AF8)),
        "Transfers" to CategoryStyle(Icons.Rounded.SwapHoriz, Color(0xFF2E90FA)),
        "Merchants & Bills" to CategoryStyle(Icons.Rounded.Storefront, Color(0xFFEE46BC)),
        "Cash Withdrawal" to CategoryStyle(Icons.Rounded.LocalAtm, Color(0xFF15B79E)),
        "Food & Shopping" to CategoryStyle(Icons.Rounded.ShoppingBag, Color(0xFFEF6820)),
        "Transport" to CategoryStyle(Icons.Rounded.DirectionsCar, Color(0xFF0BA5EC)),
        "Electricity" to CategoryStyle(Icons.Rounded.Bolt, Color(0xFFF5B800)),
        "Water" to CategoryStyle(Icons.Rounded.WaterDrop, Color(0xFF06AED4)),
        "TV & Internet" to CategoryStyle(Icons.Rounded.Tv, Color(0xFF6172F3)),
        "Education" to CategoryStyle(Icons.Rounded.School, Color(0xFF66C61C)),
        "Health" to CategoryStyle(Icons.Rounded.LocalHospital, Color(0xFFF04438)),
        "Taxes & Govt" to CategoryStyle(Icons.Rounded.AccountBalance, Color(0xFF7F56D9)),
        "Bank & Savings" to CategoryStyle(Icons.Rounded.Savings, Color(0xFF155EEF)),
        "Interest" to CategoryStyle(Icons.Rounded.TrendingUp, Color(0xFF12B76A)),
        "Loans" to CategoryStyle(Icons.Rounded.RequestQuote, Color(0xFFDC6803)),
        "Betting" to CategoryStyle(Icons.Rounded.Casino, Color(0xFFDD2590)),
        "Income" to CategoryStyle(Icons.Rounded.SouthWest, Color(0xFF17B26A)),
        "Cash Deposit" to CategoryStyle(Icons.Rounded.AccountBalanceWallet, Color(0xFF0E9384)),
        "Salary" to CategoryStyle(Icons.Rounded.Work, Color(0xFF17B26A)),
        "Business" to CategoryStyle(Icons.Rounded.BusinessCenter, Color(0xFF099250)),
        "Family & Friends" to CategoryStyle(Icons.Rounded.People, Color(0xFF3CCB7F)),
        "Refund" to CategoryStyle(Icons.Rounded.Replay, Color(0xFF16B364)),
    )
    private val fallback = CategoryStyle(Icons.Rounded.Category, Color(0xFF85888E))

    fun of(category: String) = map[category] ?: fallback
}
