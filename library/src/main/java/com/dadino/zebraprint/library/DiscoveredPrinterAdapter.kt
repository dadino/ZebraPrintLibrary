package com.dadino.zebraprint.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.zebra.sdk.printer.discovery.DiscoveredPrinter


class DiscoveredPrinterAdapter(context: Context, data: List<DiscoveredPrinter>, val callback: (DiscoveredPrinter) -> Unit) : ArrayAdapter<DiscoveredPrinter>(context, R.layout.item_discovered_printer, data) {

	private val inflater = LayoutInflater.from(context)

	private class ViewHolder {
		var root: View? = null
		var printerName: TextView? = null
		var printerAddress: TextView? = null
	}

	private var lastPosition = -1
	override fun getView(position: Int, view: View?, parent: ViewGroup): View {
		val returnView: View
		val printer: DiscoveredPrinter? = getItem(position)
		val viewHolder: ViewHolder
		if (view == null) {
			viewHolder = ViewHolder()
			returnView = inflater.inflate(R.layout.item_discovered_printer, parent, false)
			viewHolder.root = returnView.findViewById(R.id.printer_root)
			viewHolder.printerName = returnView.findViewById(R.id.printer_name)
			viewHolder.printerAddress = returnView.findViewById(R.id.printer_address)
			returnView.tag = viewHolder

		} else {
			returnView = view
			viewHolder = view.tag as ViewHolder
		}

		if (printer != null) {
			lastPosition = position
			viewHolder.printerName?.text = printer.getFriendlyName()
			viewHolder.printerAddress?.text = printer.address
			viewHolder.root?.setOnClickListener { callback(printer) }
		}
		return returnView
	}
}