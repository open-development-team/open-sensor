package com.opendevelopment.opensensor.ui.theme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.opendevelopment.R

object IconToast {

    fun show(context: Context, message: CharSequence, @DrawableRes iconResId: Int = R.mipmap.ic_launcher, duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            val drawable = ContextCompat.getDrawable(context, iconResId)
            if (drawable == null) {
                Toast.makeText(context, message, duration).show()
                return@post
            }

            // Standard icon size for Toasts is usually around 24dp
            val size = (24 * context.resources.displayMetrics.density).toInt()
            drawable.setBounds(0, 0, size, size)

            val sb = SpannableStringBuilder("  ").append(message)
            val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
            sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            Toast.makeText(context, sb, duration).show()
        }
    }
}
