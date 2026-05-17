package com.sysadmindoc.snapcrop

import android.graphics.Bitmap

internal fun Bitmap.CompressFormat.isWebpFormat(): Boolean =
    name.startsWith("WEBP")
