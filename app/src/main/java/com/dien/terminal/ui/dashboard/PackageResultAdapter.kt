package com.dien.terminal.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dien.terminal.R
import com.dien.terminal.core.pm.PackageSearchResult

class PackageResultAdapter(
    private val onInstall: (PackageSearchResult) -> Unit
) : RecyclerView.Adapter<PackageResultAdapter.VH>() {

    private val items = mutableListOf<PackageSearchResult>()

    fun submit(newItems: List<PackageSearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.TextView = view.findViewById(R.id.pkg_name)
        val version: android.widget.TextView = view.findViewById(R.id.pkg_version)
        val installBtn: android.widget.Button = view.findViewById(R.id.btn_install)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_package_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.version.text = item.version
        holder.installBtn.setOnClickListener { onInstall(item) }
    }

    override fun getItemCount() = items.size
}
