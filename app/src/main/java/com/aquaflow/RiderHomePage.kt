package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquaflow.utils.MobileOrder
import com.aquaflow.utils.OrderApi
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class RiderHomePage : AppCompatActivity() {

    private lateinit var tvRiderName: TextView
    private lateinit var rvActiveOrders: RecyclerView
    private lateinit var rvNewOrders: RecyclerView
    private lateinit var emptyNewOrders: LinearLayout
    private lateinit var loadingOverlay: View

    private lateinit var cardBulkConfirmPickup: View
    private lateinit var cardBulkStartDelivery: View
    private lateinit var tvConfirmPickupSelected: TextView
    private lateinit var tvStartDeliverySelected: TextView
    private lateinit var btnConfirmPickupSelectAll: MaterialButton
    private lateinit var btnConfirmPickupClear: MaterialButton
    private lateinit var btnConfirmPickupBulk: MaterialButton
    private lateinit var btnStartDeliverySelectAll: MaterialButton
    private lateinit var btnStartDeliveryClear: MaterialButton
    private lateinit var btnStartDeliveryBulk: MaterialButton

    private lateinit var activeAdapter: RiderOrderAdapter
    private lateinit var newAdapter: RiderOrderAdapter

    private val allOrders = mutableListOf<MobileOrder>()
    private val selectedConfirmIds = mutableSetOf<String>()
    private val selectedDispatchIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_rider_home)

        initializeViews()
        setupRecycler()
        setupBottomNav()
        loadOrdersFromBackend()
    }

    override fun onResume() {
        super.onResume()
        loadOrdersFromBackend()
    }

    private fun initializeViews() {
        tvRiderName = findViewById(R.id.tvRiderName)
        rvActiveOrders = findViewById(R.id.rvActiveOrders)
        rvNewOrders = findViewById(R.id.rvNewOrders)
        emptyNewOrders = findViewById(R.id.emptyNewOrders)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        cardBulkConfirmPickup = findViewById(R.id.cardBulkConfirmPickup)
        cardBulkStartDelivery = findViewById(R.id.cardBulkStartDelivery)
        tvConfirmPickupSelected = findViewById(R.id.tvConfirmPickupSelected)
        tvStartDeliverySelected = findViewById(R.id.tvStartDeliverySelected)
        btnConfirmPickupSelectAll = findViewById(R.id.btnConfirmPickupSelectAll)
        btnConfirmPickupClear = findViewById(R.id.btnConfirmPickupClear)
        btnConfirmPickupBulk = findViewById(R.id.btnConfirmPickupBulk)
        btnStartDeliverySelectAll = findViewById(R.id.btnStartDeliverySelectAll)
        btnStartDeliveryClear = findViewById(R.id.btnStartDeliveryClear)
        btnStartDeliveryBulk = findViewById(R.id.btnStartDeliveryBulk)

        val name = getSharedPreferences("auth", MODE_PRIVATE).getString("name", null)
        tvRiderName.text = if (name.isNullOrBlank()) "Rider" else name
    }

    private fun setupRecycler() {
        activeAdapter = RiderOrderAdapter(
            onAction = ::handleOrderAction,
            onItemClick = { },
            onToggleSelect = ::toggleSelect,
            enableSelection = true
        )
        rvActiveOrders.layoutManager = LinearLayoutManager(this)
        rvActiveOrders.adapter = activeAdapter

        newAdapter = RiderOrderAdapter(
            onAction = ::handleOrderAction,
            onItemClick = { },
            enableSelection = false
        )
        rvNewOrders.layoutManager = LinearLayoutManager(this)
        rvNewOrders.adapter = newAdapter
    }

    private fun loadOrdersFromBackend() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            setLoading(false)
            return
        }

        OrderApi.listMyOrders(token) { result ->
            runOnUiThread {
                result.onSuccess { orders ->
                    allOrders.clear()
                    allOrders.addAll(orders)
                    pruneSelections()
                    renderLists()
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to load orders", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun renderLists() {
        val activeOrders = allOrders.filter { isActiveAssigned(it) }
        val availableOrders = allOrders.filter { isAvailableOrder(it) }

        activeAdapter.submitList(activeOrders.map { DisplayItem.OrderItem(it) }, selectedConfirmIds, selectedDispatchIds)
        newAdapter.submitList(availableOrders.map { DisplayItem.OrderItem(it) })

        emptyNewOrders.visibility = if (availableOrders.isEmpty()) View.VISIBLE else View.GONE
        updateBulkCards(activeOrders)
    }

    private fun isActiveAssigned(order: MobileOrder): Boolean {
        if (isTerminalStatus(order.status)) return false
        val assigned = order.assignedToMe || !order.assignedRiderId.isNullOrBlank()
        return assigned
    }

    private fun isAvailableOrder(order: MobileOrder): Boolean {
        val pending = order.status.equals("PENDING", true)
        val unassigned = order.assignedRiderId.isNullOrBlank() && !order.assignedToMe
        return pending && unassigned
    }

    private fun handleOrderAction(order: MobileOrder, action: RiderAction) {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)
        val callback: (Result<MobileOrder>) -> Unit = { result ->
            runOnUiThread {
                result.onSuccess { updated ->
                    replaceOrder(updated)
                    selectedConfirmIds.remove(updated.id)
                    selectedDispatchIds.remove(updated.id)
                    renderLists()
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Failed to update order", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }

        when (action) {
            RiderAction.ACCEPT_ORDER -> OrderApi.confirmOrder(token, order.id, callback)
            RiderAction.CONFIRM_PICKUP -> OrderApi.confirmPickup(token, order.id, callback)
            RiderAction.CANCEL_PICKUP -> OrderApi.cancelPickup(token, order.id, callback)
            RiderAction.START_DELIVERY -> OrderApi.startDelivery(token, order.id, callback)
            RiderAction.MARK_DELIVERED -> OrderApi.markDelivered(token, order.id, callback)
            RiderAction.CONFIRM_PAYMENT -> OrderApi.confirmPayment(token, order.id, callback)
        }
    }

    private fun toggleSelect(orderId: String, status: String, isChecked: Boolean) {
        val normalized = status.uppercase()
        if (normalized == "CONFIRMED") {
            if (isChecked) selectedConfirmIds.add(orderId) else selectedConfirmIds.remove(orderId)
        } else if (normalized == "PICKED_UP") {
            if (isChecked) selectedDispatchIds.add(orderId) else selectedDispatchIds.remove(orderId)
        }
        renderLists()
    }

    private fun replaceOrder(updated: MobileOrder) {
        val index = allOrders.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            allOrders[index] = updated
        } else {
            allOrders.add(updated)
        }
    }


    private fun pruneSelections() {
        val confirmed = allOrders.filter { it.status.equals("CONFIRMED", true) }.map { it.id }.toSet()
        val picked = allOrders.filter { it.status.equals("PICKED_UP", true) }.map { it.id }.toSet()
        selectedConfirmIds.retainAll(confirmed)
        selectedDispatchIds.retainAll(picked)
    }

    private fun updateBulkCards(activeOrders: List<MobileOrder>) {
        val confirmed = activeOrders.filter { it.status.equals("CONFIRMED", true) }
        val picked = activeOrders.filter { it.status.equals("PICKED_UP", true) }

        cardBulkConfirmPickup.visibility = if (confirmed.isNotEmpty()) View.VISIBLE else View.GONE
        cardBulkStartDelivery.visibility = if (picked.isNotEmpty()) View.VISIBLE else View.GONE

        tvConfirmPickupSelected.text = "Selected: ${selectedConfirmIds.size} orders"
        tvStartDeliverySelected.text = "Selected: ${selectedDispatchIds.size} orders"

        btnConfirmPickupSelectAll.setOnClickListener {
            selectedConfirmIds.clear()
            selectedConfirmIds.addAll(confirmed.map { it.id })
            renderLists()
        }
        btnConfirmPickupClear.setOnClickListener {
            selectedConfirmIds.clear()
            renderLists()
        }
        btnStartDeliverySelectAll.setOnClickListener {
            selectedDispatchIds.clear()
            selectedDispatchIds.addAll(picked.map { it.id })
            renderLists()
        }
        btnStartDeliveryClear.setOnClickListener {
            selectedDispatchIds.clear()
            renderLists()
        }

        btnConfirmPickupBulk.isEnabled = selectedConfirmIds.isNotEmpty()
        btnStartDeliveryBulk.isEnabled = selectedDispatchIds.isNotEmpty()

        btnConfirmPickupBulk.setOnClickListener { performBulkConfirmPickup() }
        btnStartDeliveryBulk.setOnClickListener { performBulkStartDelivery() }
    }

    private fun performBulkConfirmPickup() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank() || selectedConfirmIds.isEmpty()) return
        setLoading(true)
        val ids = selectedConfirmIds.toList()
        OrderApi.bulkConfirmPickup(token, ids) { result ->
            runOnUiThread {
                result.onSuccess {
                    allOrders.replaceAll { order ->
                        if (selectedConfirmIds.contains(order.id)) order.copy(status = "PICKED_UP") else order
                    }
                    selectedConfirmIds.clear()
                    renderLists()
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Failed to confirm pickup", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun performBulkStartDelivery() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank() || selectedDispatchIds.isEmpty()) return
        setLoading(true)
        val ids = selectedDispatchIds.toList()
        OrderApi.bulkStartDelivery(token, ids) { result ->
            runOnUiThread {
                result.onSuccess {
                    allOrders.replaceAll { order ->
                        if (selectedDispatchIds.contains(order.id)) order.copy(status = "OUT_FOR_DELIVERY") else order
                    }
                    selectedDispatchIds.clear()
                    renderLists()
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Failed to start delivery", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun isTerminalStatus(status: String): Boolean {
        val s = status.uppercase()
        return s == "COMPLETED" || s == "CANCELLED"
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, RiderOrderPage::class.java))
                    true
                }
                R.id.navigation_messages -> {
                    startActivity(Intent(this, RiderMessagePage::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, RiderProfilePage::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
