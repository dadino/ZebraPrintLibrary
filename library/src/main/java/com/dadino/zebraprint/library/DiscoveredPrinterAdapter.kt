package com.dadino.zebraprint.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView


class DiscoveredPrinterAdapter(context: Context, data: List<Printer>, val callback: (Printer) -> Unit) : ArrayAdapter<Printer>(context, R.layout.item_discovered_printer, data) {

	private val inflater = LayoutInflater.from(context)

	private class ViewHolder {
		var root: View? = null
		var printerName: TextView? = null
		var printerAddress: TextView? = null
		var printerTypeIcon: ImageView? = null
	}

	private var lastPosition = -1
	override fun getView(position: Int, view: View?, parent: ViewGroup): View {
		val returnView: View
		val printer: Printer? = getItem(position)
		val viewHolder: ViewHolder
		if (view == null) {
			viewHolder = ViewHolder()
			returnView = inflater.inflate(R.layout.item_discovered_printer, parent, false)
			viewHolder.root = returnView.findViewById(R.id.printer_root)
			viewHolder.printerName = returnView.findViewById(R.id.printer_name)
			viewHolder.printerAddress = returnView.findViewById(R.id.printer_address)
			viewHolder.printerTypeIcon = returnView.findViewById(R.id.printer_type_icon)
			returnView.tag = viewHolder

		} else {
			returnView = view
			viewHolder = view.tag as ViewHolder
		}

		if (printer != null) {
			lastPosition = position
			viewHolder.printerName?.text = printer.friendlyName
			viewHolder.printerAddress?.text = printer.address
			viewHolder.printerTypeIcon?.setImageResource(printer.type.icon)
			viewHolder.root?.setOnClickListener { callback(printer) }
		}
		return returnView
	}
}