package com.opendevelopment.opensensor.ui.theme

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.opendevelopment.R

object IconToast {

    fun show(context: Context, message: CharSequence, @DrawableRes iconResId: Int = R.mipmap.ic_launcher, duration: Int = Toast.LENGTH_SHORT) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null)

        val icon = layout.findViewById<ImageView>(R.id.toast_icon)
        icon.setImageResource(iconResId)

        val text = layout.findViewById<TextView>(R.id.toast_message)
        text.text = message

        with(Toast(context)) {
            this.duration = duration
            this.view = layout
            show()
        }
    }
}
