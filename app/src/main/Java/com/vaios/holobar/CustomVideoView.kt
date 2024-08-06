package com.vaios.holobar

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class CustomVideoView : VideoView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun performClick(): Boolean {
        super.performClick()
        // You can add custom behavior here if needed
        return true
    }
}