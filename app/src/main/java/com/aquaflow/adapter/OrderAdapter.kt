package com.aquaflow.data


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.aquaflow.R
import com.aquaflow.data.Order
import com.aquaflow.data.OrderStatus
import com.aquaflow.ui.OrderStatusBadgeMapper
import com.google.android.material.button.MaterialButton

class OrderAdapter(private val orders: List<Order>) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvOrderDate)
        val tvBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvOrderTitle)
        val tvArrival: TextView = view.findViewById(R.id.tvArrivalTime)
        val btnPrimary: MaterialButton = view.findViewById(R.id.btnPrimary)
        val btnSecondary: MaterialButton = view.findViewById(R.id.btnSecondary)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.tvDate.text = order.timeStamp
        holder.tvTitle.text = "${order.quantity}× ${order.itemName}"
        holder.tvArrival.text = "Arriving in ${order.arrivalTime}"
        OrderStatusBadgeMapper.apply(holder.tvBadge, order.status)

        // --- CONDITIONAL RENDERING (SAME LOGIC AS HOME) ---
        when (order.status) {
            OrderStatus.PENDING -> {
                holder.btnPrimary.visibility = View.VISIBLE
                holder.btnPrimary.text = "Track"
                holder.btnSecondary.visibility = View.GONE
            }
            OrderStatus.CONFIRMED,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED,
            OrderStatus.PENDING_PAYMENT -> {
                holder.btnPrimary.visibility = View.VISIBLE
                holder.btnPrimary.text = "Track"
                holder.btnSecondary.visibility = View.VISIBLE
                holder.btnSecondary.text = "Message"
            }
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED -> {
                holder.btnPrimary.visibility = View.GONE
                holder.btnSecondary.visibility = View.GONE
            }
        }

        // --- CLICK LISTENERS ---
        holder.btnPrimary.setOnClickListener {
            // Logic for Track or View Details
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order_card, parent, false)
        return OrderViewHolder(view)
    }

    override fun getItemCount() = orders.size
}
