package com.dtt.backgroundremoveapplication

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dtt.backgroundremoveapplication.ui.theme.BackGroundRemoveApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackGroundRemoveApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DisplayImagesWithBackgroundRemoval(this@MainActivity)
                }
            }
        }
    }

    // Convert Drawable to Bitmap safely
    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e("BitmapError", "Error converting drawable to bitmap: ${e.message}")
            null
        }
    }

    // Apply mask to remove background from image
    fun applyMaskToBitmap(original: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null
        return result
    }

    // Create transparent mask from segmentation
    fun createTransparentMask(mask: SegmentationMask, width: Int, height: Int): Bitmap {
        val buffer = mask.buffer
        buffer.rewind()

        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val threshold = 0.4f // Adjust this for better accuracy

        for (i in pixels.indices) {
            val confidence = buffer.float
            pixels[i] = if (confidence > threshold) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT
        }

        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return maskBitmap
    }
}

@Composable
fun DisplayImagesWithBackgroundRemoval(mainActivity: MainActivity) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()), // Allow scrolling if images overflow
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            ChangeImageBackground(mainActivity, R.drawable.kundan_passport)
            ChangeImageBackground(mainActivity, R.drawable.sample4)
            ChangeImageBackground(mainActivity, R.drawable.sample1)
            ChangeImageBackground(mainActivity, R.drawable.sample2)

        }
    }



}


@Composable
fun ChangeImageBackground(mainActivity: MainActivity, id: Int) {
    val context = LocalContext.current
    val processedBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Original Image",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Original Image
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(60.dp))
                .border(2.dp, Color.Gray, RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = id),
                contentDescription = "Original Image",
                modifier = Modifier.width(200.dp).height(200.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Processed Image / Progress Indicator
        Text(
            text = "Processed Image",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(60.dp))
                .border(2.dp, Color.LightGray, RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading.value -> CircularProgressIndicator()
                processedBitmap.value != null -> Image(
                    bitmap = processedBitmap.value!!,
                    contentDescription = "Processed Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Process Button
        Button(onClick = {
            isLoading.value = true // Start progress indicator
            try {
                val drawable = ContextCompat.getDrawable(context, id)
                val bitmap = drawable?.let { mainActivity.drawableToBitmap(it) }
                if (bitmap == null) {
                    Log.e("ImageProcessing", "Failed to convert drawable to bitmap.")
                    isLoading.value = false
                    return@Button
                }

                val segmenter = Segmentation.getClient(
                    SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .build()
                )

                val image = InputImage.fromBitmap(bitmap, 0)

                segmenter.process(image)
                    .addOnSuccessListener { segmentationMask ->
                        val maskBitmap = mainActivity.createTransparentMask(segmentationMask, bitmap.width, bitmap.height)
                        val resultBitmap = mainActivity.applyMaskToBitmap(bitmap, maskBitmap)
                        processedBitmap.value = resultBitmap.asImageBitmap()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ImageProcessing", "Error processing image: ${e.message}")
                    }
                    .addOnCompleteListener {
                        isLoading.value = false // Stop progress indicator
                    }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "Unexpected error: ${e.message}")
                isLoading.value = false
            }
        }) {
            Text("Process Image")
        }
    }
}

