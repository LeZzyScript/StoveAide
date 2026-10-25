package com.example.stoveaide

import com.example.stoveaide.views.DaysUsageActivity as ViewsDaysUsageActivity

/**
 * Root-package DaysUsageActivity for accessing daily stove usage sessions and detail drill-down.
 * Inherits all session binding, chart gauge rendering, and deletion behavior from ViewsDaysUsageActivity.
 */
open class DaysUsageActivity : ViewsDaysUsageActivity() {

    companion object {
        const val EXTRA_LOG = ViewsDaysUsageActivity.EXTRA_LOG
        const val EXTRA_LOG_ID = ViewsDaysUsageActivity.EXTRA_LOG_ID
        const val EXTRA_DATE = ViewsDaysUsageActivity.EXTRA_DATE
    }
}
