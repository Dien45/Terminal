package com.dien.terminal.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dien.terminal.R
import com.dien.terminal.core.logging.LogEntry
import com.dien.terminal.core.logging.OperationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private val onClick: (LogEntry) -> Unit) : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = mutableListOf<LogEntry>()
    private val fmt = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id", "ID"))

    fun submit(newItems: List<LogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val title: android.widget.TextView = view.findViewById(R.id.log_title)
        val status: android.widget.TextView = view.findViewById(R.id.log_status)
        val subtitle: android.widget.TextView = view.findViewById(R.id.log_subtitle)
        val summary: android.widget.TextView = view.findViewById(R.id.log_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = "${item.type} • ${fmt.format(Date(item.startTime))}"
        holder.summary.text = item.summary
        val context = holder.itemView.context
        val (colorRes, label) = when (item.status) {
            OperationStatus.SUCCESS.name -> R.color.log_success to "SUKSES"
            OperationStatus.FAILED.name -> R.color.log_error to "GAGAL"
            else -> R.color.primary_blue to "BERJALAN"
        }
        holder.status.text = label
        holder.status.setTextColor(context.getColor(colorRes))
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
