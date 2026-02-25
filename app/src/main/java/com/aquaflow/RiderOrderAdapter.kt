package com.aquaflow

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aquaflow.utils.MobileOrder
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.card.MaterialCardView

sealed class DisplayItem {
    data class HeaderItem(val title: String, val count: Int) : DisplayItem()
    data class OrderItem(val order: MobileOrder) : DisplayItem()
}

class RiderOrderAdapter(
    private val onAction: (MobileOrder, RiderAction) -> Unit,
    private val onItemClick: (MobileOrder) -> Unit,
    private val onToggleSelect: (String, String, Boolean) -> Unit = { _, _, _ -> },
    private val enableSelection: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DisplayItem>()
    private val selectedConfirmIds = mutableSetOf<String>()
    private val selectedDispatchIds = mutableSetOf<String>()

    fun submitList(
        next: List<DisplayItem>,
        selectedConfirm: Set<String> = emptySet(),
        selectedDispatch: Set<String> = emptySet()
    ) {
        items.clear()
        items.addAll(next)
        selectedConfirmIds.clear()
        selectedConfirmIds.addAll(selectedConfirm)
        selectedDispatchIds.clear()
        selectedDispatchIds.addAll(selectedDispatch)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is DisplayItem.HeaderItem) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val view = inflater.inflate(R.layout.item_rider_order_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_rider_order, parent, false)
            RiderOrderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DisplayItem.HeaderItem -> (holder as HeaderViewHolder).bind(item)
            is DisplayItem.OrderItem -> (holder as RiderOrderViewHolder).bind(
                item.order,
                onAction,
                onItemClick,
                onToggleSelect,
                selectedConfirmIds,
                selectedDispatchIds,
                enableSelection
            )
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeaderTitle = itemView.findViewById<TextView>(R.id.tvHeaderTitle)
        private val tvHeaderCount = itemView.findViewById<TextView>(R.id.tvHeaderCount)

        fun bind(item: DisplayItem.HeaderItem) {
            tvHeaderTitle.text = item.title
            tvHeaderCount.text = "${item.count} orders"
        }
    }

    class RiderOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
        private val tvEta = itemView.findViewById<TextView>(R.id.tvEta)
        private val tvCustomerName = itemView.findViewById<TextView>(R.id.tvCustomerName)
        private val tvAddress = itemView.findViewById<TextView>(R.id.tvAddress)
        private val tvOrderIdValue = itemView.findViewById<TextView>(R.id.tvOrderIdValue)
        private val tvQuantityValue = itemView.findViewById<TextView>(R.id.tvQuantityValue)
        private val tvPaymentValue = itemView.findViewById<TextView>(R.id.tvPaymentValue)
        private val layoutActionSingle = itemView.findViewById<LinearLayout>(R.id.layoutActionSingle)
        private val layoutActionDouble = itemView.findViewById<LinearLayout>(R.id.layoutActionDouble)
        private val layoutActionDisabled = itemView.findViewById<LinearLayout>(R.id.layoutActionDisabled)
        private val btnPrimaryAction = itemView.findViewById<MaterialButton>(R.id.btnPrimaryAction)
        private val btnConfirmDelivery = itemView.findViewById<MaterialButton>(R.id.btnConfirmDelivery)
        private val btnCancelDelivery = itemView.findViewById<MaterialButton>(R.id.btnCancelDelivery)
        private val btnDisabled = itemView.findViewById<MaterialButton>(R.id.btnDisabled)
        private val cbSelect = itemView.findViewById<MaterialCheckBox>(R.id.cbSelect)
        private val card = itemView as MaterialCardView

        fun bind(
            order: MobileOrder,
            onAction: (MobileOrder, RiderAction) -> Unit,
            onItemClick: (MobileOrder) -> Unit,
            onToggleSelect: (String, String, Boolean) -> Unit,
            selectedConfirm: Set<String>,
            selectedDispatch: Set<String>,
            enableSelection: Boolean
        ) {
            val orderCode = order.orderCode ?: order.id.takeLast(4)
            tvCustomerName.text = order.customerName ?: "Customer $orderCode"
            tvAddress.text = order.customerAddress ?: "Address unavailable"
            tvOrderIdValue.text = "#$orderCode"
            tvQuantityValue.text = "${order.quantity} Gallons"
            tvPaymentValue.text = paymentLabel(order.paymentMethod)
            tvEta.text = order.etaText ?: "ETA pending"
            applyStatusChip(order.status, tvStatus)
            applyActions(order, onAction)
            applySelection(order, onToggleSelect, selectedConfirm, selectedDispatch, enableSelection)

            itemView.setOnClickListener { onItemClick(order) }
        }

        private fun applySelection(
            order: MobileOrder,
            onToggleSelect: (String, String, Boolean) -> Unit,
            selectedConfirm: Set<String>,
            selectedDispatch: Set<String>,
            enableSelection: Boolean
        ) {
            if (!enableSelection) {
                setCardSelected(false)
                return
            }
            val normalized = order.status.uppercase()
            if (normalized == "CONFIRMED" || normalized == "PICKED_UP") {
                val checked = if (normalized == "CONFIRMED") {
                    selectedConfirm.contains(order.id)
                } else {
                    selectedDispatch.contains(order.id)
                }
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = checked
                setCardSelected(checked)
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    setCardSelected(isChecked)
                    onToggleSelect(order.id, order.status, isChecked)
                }
            } else {
                setCardSelected(false)
            }
        }

        private fun setCardSelected(isSelected: Boolean) {
            card.strokeWidth = if (isSelected) 2 else 1
            val colorRes = if (isSelected) R.color.primary else R.color.border_light
            card.setStrokeColor(itemView.context.getColor(colorRes))
        }

        private fun applyActions(order: MobileOrder, onAction: (MobileOrder, RiderAction) -> Unit) {
            layoutActionSingle.visibility = View.GONE
            layoutActionDouble.visibility = View.GONE
            layoutActionDisabled.visibility = View.GONE

            when (order.status.uppercase()) {
                "PENDING" -> {
                    layoutActionSingle.visibility = View.VISIBLE
                    btnPrimaryAction.text = "Accept Order"
                    btnPrimaryAction.setOnClickListener { onAction(order, RiderAction.ACCEPT_ORDER) }
                }
                "CONFIRMED" -> {
                    layoutActionDouble.visibility = View.VISIBLE
                    btnConfirmDelivery.text = "Confirm Pickup"
                    btnCancelDelivery.text = "Cancel"
                    btnConfirmDelivery.setOnClickListener { onAction(order, RiderAction.CONFIRM_PICKUP) }
                    btnCancelDelivery.setOnClickListener { onAction(order, RiderAction.CANCEL_PICKUP) }
                }
                "PICKED_UP" -> {
                    layoutActionSingle.visibility = View.VISIBLE
                    btnPrimaryAction.text = "Start Delivery"
                    btnPrimaryAction.setOnClickListener { onAction(order, RiderAction.START_DELIVERY) }
                }
                "OUT_FOR_DELIVERY" -> {
                    layoutActionSingle.visibility = View.VISIBLE
                    btnPrimaryAction.text = "Mark as Delivered"
                    btnPrimaryAction.setOnClickListener { onAction(order, RiderAction.MARK_DELIVERED) }
                }
                "DELIVERED", "PENDING_PAYMENT" -> {
                    if (order.paymentMethod.uppercase() == "COD" || order.paymentMethod.uppercase() == "CASH") {
                        layoutActionSingle.visibility = View.VISIBLE
                        btnPrimaryAction.text = "Confirm Payment"
                        btnPrimaryAction.setOnClickListener { onAction(order, RiderAction.CONFIRM_PAYMENT) }
                    } else {
                        layoutActionDisabled.visibility = View.VISIBLE
                        btnDisabled.text = "Awaiting Payment"
                    }
                }
                "COMPLETED" -> {
                    layoutActionDisabled.visibility = View.VISIBLE
                    btnDisabled.text = "Completed"
                }
                "CANCELLED" -> {
                    layoutActionDisabled.visibility = View.VISIBLE
                    btnDisabled.text = "Order Cancelled"
                }
                else -> Unit
            }
        }

        private fun paymentLabel(method: String): String {
            val upper = method.uppercase()
            return if (upper == "GCASH") "GCASH" else "Cash"
        }

        private fun applyStatusChip(status: String, target: TextView) {
            when (status.uppercase()) {
                "PENDING" -> {
                    target.text = "Available"
                    target.setBackgroundResource(R.drawable.bg_status_pending)
                }
                "CONFIRMED" -> {
                    target.text = "Confirmed"
                    target.setBackgroundResource(R.drawable.bg_status_confirmed)
                }
                "PICKED_UP" -> {
                    target.text = "Picked Up"
                    target.setBackgroundResource(R.drawable.bg_status_picked_up)
                }
                "OUT_FOR_DELIVERY" -> {
                    target.text = "Delivering"
                    target.setBackgroundResource(R.drawable.bg_status_out_for_delivery)
                }
                "DELIVERED" -> {
                    target.text = "Delivered"
                    target.setBackgroundResource(R.drawable.bg_status_delivered)
                }
                "PENDING_PAYMENT" -> {
                    target.text = "Pending Payment"
                    target.setBackgroundResource(R.drawable.bg_status_pending_payment)
                }
                "COMPLETED" -> {
                    target.text = "Completed"
                    target.setBackgroundResource(R.drawable.bg_status_completed)
                }
                "CANCELLED" -> {
                    target.text = "Cancelled"
                    target.setBackgroundResource(R.drawable.bg_badge_grey)
                }
                else -> {
                    target.text = status.replace("_", " ")
                    target.setBackgroundResource(R.drawable.bg_status_container)
                }
            }
        }
    }
}
