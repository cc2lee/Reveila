package com.reveila.error

/**
 * Bundles a collection of exceptions that can be inspected or rethrown together.
 */
open class ExceptionCollection : Exception {

    private val exceptions = mutableListOf<Throwable>()

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(throwable: Throwable?) : super() {
        if (throwable != null) {
            addException(throwable)
        }
    }

    constructor(message: String?, throwable: Throwable?) : super(message) {
        if (throwable != null) {
            addException(throwable)
        }
    }

    fun addException(throwable: Throwable?) {
        if (throwable != null) {
            exceptions.add(throwable)
        }
    }

    fun getExceptions(): List<Throwable> {
        return exceptions.toList()
    }

    fun setExceptions(list: List<Throwable>?) {
        exceptions.clear()
        if (list != null) {
            exceptions.addAll(list)
        }
    }

    override fun toString(): String {
        return ErrorUtil.toString(this)
    }

    override val message: String?
        get() {
            if (exceptions.isNotEmpty()) {
                val strBuf = StringBuilder()
                val endl = "\n"
                strBuf.append(endl).append("Exceptions:")
                for (t in exceptions) {
                    strBuf.append(endl)
                    strBuf.append(ErrorUtil.toString(t))
                }
                return strBuf.toString()
            } else {
                return super.message
            }
        }

    fun isEmpty(): Boolean {
        return exceptions.isEmpty()
    }
}
