package com.aquaflow.data


import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.aquaflow.R
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

        // --- CONDITIONAL RENDERING (SAME LOGIC AS HOME) ---
        when (order.status) {
            OrderStatus.PENDING -> {
                holder.tvBadge.text = "Pending"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange_light)
                holder.tvBadge.setTextColor(Color.parseColor("#FFB74D"))

                // One Button visible (View Details)
                holder.btnPrimary.text = "View details"
                holder.btnSecondary.visibility = View.GONE
            }
            OrderStatus.CONFIRMED -> {
                holder.tvBadge.text = "Ordered"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                holder.tvBadge.setTextColor(Color.parseColor("#757575"))

                //BUTTONS VISIBLE
                holder.btnPrimary.text = "Track"
                holder.btnSecondary.visibility = View.VISIBLE
                holder.btnSecondary.text = "Message"
            }
            OrderStatus.OUT_FOR_DELIVERY -> {
                holder.tvBadge.text = "Out for delivery"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue_solid)
                holder.tvBadge.setTextColor(Color.parseColor("#1E88E5"))

                //BUTTONS VISIBLE
                holder.btnPrimary.text = "Track"
                holder.btnSecondary.visibility = View.VISIBLE
                holder.btnSecondary.text = "Message"
            }
            OrderStatus.GALLON_PICKUP -> {
                holder.tvBadge.text = "Picking up"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange_light)
                holder.tvBadge.setTextColor(Color.parseColor("#FFB74D"))

                //BUTTONS VISIBLE
                holder.btnPrimary.text = "Track"
                holder.btnSecondary.visibility = View.VISIBLE
                holder.btnSecondary.text = "Message"
            }
            OrderStatus.COMPLETED -> {
                holder.tvBadge.text = "Completed"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_green)
                holder.tvBadge.setTextColor(Color.parseColor("#FFB74D"))

                // One Button visible (View Details)
                holder.btnPrimary.text = "View details"
                holder.btnSecondary.visibility = View.GONE
            }
            OrderStatus.CANCELLED -> {
                holder.tvBadge.text = "Cancelled"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_red)
                holder.tvBadge.setTextColor(Color.parseColor("#FFB74D"))

                // One Button visible (View Details)
                holder.btnPrimary.text = "View details"
                holder.btnSecondary.visibility = View.GONE
            }
            else -> {
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