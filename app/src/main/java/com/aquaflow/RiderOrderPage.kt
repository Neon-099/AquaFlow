package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

class RiderOrderPage : AppCompatActivity() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var adapter: RiderOrderAdapter
    private lateinit var inputSearch: TextInputEditText
    private lateinit var tabOrders: TabLayout
    private lateinit var tvTodayLabel: TextView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var tvErrorBanner: TextView
    private lateinit var loadingOverlay: View

    private val allOrders = mutableListOf<MobileOrder>()
    private var currentTab = TabType.TODO
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_rider_order)

        initializeViews()
        setupRecycler()
        setupSearch()
        setupTabs()
        setupBottomNav()
        loadOrdersFromBackend()
    }

    override fun onResume() {
        super.onResume()
        loadOrdersFromBackend()
    }

    private fun initializeViews() {
        rvOrders = findViewById(R.id.rvOrders)
        inputSearch = findViewById(R.id.inputSearch)
        tabOrders = findViewById(R.id.tabOrders)
        tvTodayLabel = findViewById(R.id.tvTodayLabel)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        tvErrorBanner = findViewById(R.id.tvErrorBanner)
        loadingOverlay = findViewById(R.id.loadingOverlay)
    }

    private fun setupRecycler() {
        adapter = RiderOrderAdapter(
            onAction = ::handleOrderAction,
            onItemClick = { },
            enableSelection = false
        )
        rvOrders.layoutManager = LinearLayoutManager(this)
        rvOrders.adapter = adapter
    }

    private fun setupSearch() {
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty().trim()
                applyFilters()
            }
        })
    }

    private fun setupTabs() {
        tabOrders.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = if (tab?.position == 1) TabType.COMPLETED else TabType.TODO
                tvTodayLabel.visibility = if (currentTab == TabType.TODO) View.VISIBLE else View.GONE
                applyFilters()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun loadOrdersFromBackend() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            setLoading(false)
            return
        }

        OrderApi.listMyOrders(token) { result ->
            runOnUiThread {
                result.onSuccess { orders ->
                    tvErrorBanner.visibility = View.GONE
                    allOrders.clear()
                    allOrders.addAll(orders)
                    applyFilters()
                    setLoading(false)
                }.onFailure {
                    tvErrorBanner.visibility = View.VISIBLE
                    tvErrorBanner.text = it.message ?: "You're offline. Some data may be outdated."
                    setLoading(false)
                }
            }
        }
    }

    private fun applyFilters() {
        val filtered = allOrders
            .filter { matchesTab(it) }
            .filter { matchesQuery(it, currentQuery) }
            .sortedWith(compareBy<MobileOrder> { isTerminalStatus(it.status) }.thenBy { it.status })

        val displayItems = buildDisplayItems(filtered)
        adapter.submitList(displayItems)
        emptyStateContainer.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvOrders.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun matchesTab(order: MobileOrder): Boolean {
        return if (currentTab == TabType.COMPLETED) {
            isTerminalStatus(order.status)
        } else {
            !isTerminalStatus(order.status)
        }
    }

    private fun matchesQuery(order: MobileOrder, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.lowercase()
        return listOfNotNull(
            order.customerName,
            order.customerAddress,
            order.orderCode
        ).any { it.lowercase().contains(needle) }
    }

    private fun buildDisplayItems(orders: List<MobileOrder>): List<DisplayItem> {
        if (currentTab == TabType.COMPLETED) {
            return orders.map { DisplayItem.OrderItem(it) }
        }

        val active = orders.filter { !isTerminalStatus(it.status) }
        val allOutForDelivery =
            active.isNotEmpty() && active.all { it.status.equals("OUT_FOR_DELIVERY", true) }

        if (!allOutForDelivery) {
            return orders.map { DisplayItem.OrderItem(it) }
        }

        val grouped = active.groupBy { it.etaText?.ifBlank { null } ?: "ETA pending" }
        val out = mutableListOf<DisplayItem>()
        for ((eta, list) in grouped) {
            out += DisplayItem.HeaderItem("ETA: $eta", list.size)
            list.forEach { out += DisplayItem.OrderItem(it) }
        }
        return out
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
                    applyFilters()
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

    private fun replaceOrder(updated: MobileOrder) {
        val index = allOrders.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            allOrders[index] = updated
        } else {
            allOrders.add(updated)
        }
    }


    private fun isTerminalStatus(status: String): Boolean {
        val s = status.uppercase()
        return s == "COMPLETED" || s == "CANCELLED"
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_orders
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_orders -> true
                R.id.navigation_home -> {
                    startActivity(Intent(this, RiderHomePage::class.java))
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

    private enum class TabType { TODO, COMPLETED }
}
