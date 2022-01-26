package com.dadino.zebraprint.library

sealed class Optional<out T> {
	class Some<out T>(val element: T) : Optional<T>()
	object None : Optional<Nothing>()

	fun element(): T? {
		return when (this) {
			is None -> null
			is Some -> element
		}
	}

	companion object {
		fun <T> create(element: T?): Optional<T> {
			return if (element != null) {
				Some(element)
			} else {
				None
			}
		}
	}
}
