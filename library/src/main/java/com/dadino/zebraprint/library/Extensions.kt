package com.dadino.zebraprint.library

import com.zebra.sdk.printer.discovery.DiscoveredPrinter

fun DiscoveredPrinter.getFriendlyName(): String? = discoveryDataMap["FRIENDLY_NAME"]
