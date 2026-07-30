package com.vloc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActionBar(
    msg: String,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(text = msg)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartMock,
                modifier = Modifier.weight(1f),
                shape = ButtonDefaults.filledTonalShape
            ) {
                Text("穿越")
            }
            Button(
                onClick = onStopMock,
                modifier = Modifier.weight(1f),
                shape = ButtonDefaults.filledTonalShape
            ) {
                Text("回归")
            }
        }
    }
}
