package com.aquaflow.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.aquaflow.R
import com.aquaflow.data.OrderStatus
import java.util.Locale

data class OrderStatusBadgeStyle(
    val label: String,
    @DrawableRes val backgroundRes: Int,
    @ColorRes val textColorRes: Int,
    @DrawableRes val iconRes: Int = R.drawable.ic_clock
)

object OrderStatusBadgeMapper {
    private const val BADGE_ICON_SIZE_DP = 11

    fun apply(view: TextView, status: OrderStatus?) {
        if (status == null) {
            view.visibility = View.GONE
            return
        }

        val style = styleFor(status)
        val color = ContextCompat.getColor(view.context, style.textColorRes)

        view.visibility = View.VISIBLE
        view.text = style.label
        view.setBackgroundResource(style.backgroundRes)
        view.setTextColor(color)

        val iconSizePx = dpToPx(view, BADGE_ICON_SIZE_DP)
        val iconDrawable = ContextCompat.getDrawable(view.context, style.iconRes)?.mutate()?.apply {
            setBounds(0, 0, iconSizePx, iconSizePx)
        }
        view.setCompoundDrawablesRelative(iconDrawable, null, null, null)
        view.compoundDrawablePadding = dpToPx(view, 6)
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color))
    }

    fun fromServerStatus(raw: String?): OrderStatus? {
        val normalized = raw
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.replace("-", "_")
            ?.replace(" ", "_")
            ?: return null

        return when (normalized) {
            "PENDING" -> OrderStatus.PENDING
            "CONFIRMED" -> OrderStatus.CONFIRMED
            "PICKED_UP" -> OrderStatus.PICKED_UP
            "OUT_FOR_DELIVERY" -> OrderStatus.OUT_FOR_DELIVERY
            "DELIVERED" -> OrderStatus.DELIVERED
            "PENDING_PAYMENT" -> OrderStatus.PENDING_PAYMENT
            "COMPLETED" -> OrderStatus.COMPLETED
            "CANCELLED", "CANCELED" -> OrderStatus.CANCELLED
            else -> null
        }
    }
    
    fun styleFor(status: OrderStatus): OrderStatusBadgeStyle {
        return when (status) {
            OrderStatus.PENDING -> OrderStatusBadgeStyle(
                label = "Pending",
                backgroundRes = R.drawable.bg_status_pending,
                textColorRes = R.color.warning,
                iconRes = R.drawable.ic_clock
            )
            OrderStatus.CONFIRMED -> OrderStatusBadgeStyle(
                label = "Confirmed",
                backgroundRes = R.drawable.bg_status_confirmed,
                textColorRes = R.color.primary,
                iconRes = R.drawable.ic_check_circle
            )
            OrderStatus.PICKED_UP -> OrderStatusBadgeStyle(
                label = "Picked Up",
                backgroundRes = R.drawable.bg_status_picked_up,
                textColorRes = R.color.primary_dark,
                iconRes = R.drawable.ic_order
            )
            OrderStatus.OUT_FOR_DELIVERY -> OrderStatusBadgeStyle(
                label = "Out for delivery",
                backgroundRes = R.drawable.bg_status_out_for_delivery,
                textColorRes = R.color.primary_dark,
                iconRes = R.drawable.ic_delivery_truck
            )
            OrderStatus.DELIVERED -> OrderStatusBadgeStyle(
                label = "Delivered",
                backgroundRes = R.drawable.bg_status_delivered,
                textColorRes = R.color.success,
                iconRes = R.drawable.ic_check_circle
            )
            OrderStatus.PENDING_PAYMENT -> OrderStatusBadgeStyle(
                label = "Pending Payment",
                backgroundRes = R.drawable.bg_status_pending_payment,
                textColorRes = R.color.warning,
                iconRes = R.drawable.ic_payment
            )
            OrderStatus.COMPLETED -> OrderStatusBadgeStyle(
                label = "Completed",
                backgroundRes = R.drawable.bg_status_completed,
                textColorRes = R.color.secondary,
                iconRes = R.drawable.ic_check_circle
            )
            OrderStatus.CANCELLED -> OrderStatusBadgeStyle(
                label = "Cancelled",
                backgroundRes = R.drawable.bg_badge_red,
                textColorRes = R.color.text_white,
                iconRes = R.drawable.ic_remove
            )
        }
    }

    private fun dpToPx(view: TextView, dp: Int): Int {
        val density = view.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
