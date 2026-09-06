package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val IsoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "날짜를 선택하세요",
    minDate: LocalDate = LocalDate.of(1940, 1, 1),
    maxDate: LocalDate = LocalDate.now(),
) {
    val colors = LocalKidhackColors.current
    var showPicker by remember { mutableStateOf(false) }
    val selected = value.toLocalDateOrNull()
    val displayValue = selected?.toDisplayDate().orEmpty()
    val initialMillis = (selected ?: maxDate.minusYears(10)).toUtcMillis()
    val yearRange = minDate.year..maxDate.year
    val minMillis = minDate.toUtcMillis()
    val maxMillis = maxDate.toUtcMillis()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                readOnly = true,
                textStyle = TextStyle(
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 14.sp,
                ),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 14.sp,
                        color = colors.muted,
                    )
                },
                trailingIcon = { Text("📅", fontSize = 16.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    disabledContainerColor = colors.surface,
                    focusedBorderColor = colors.brand,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showPicker = true },
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = yearRange,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(millis.toIsoDate())
                        }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text("확인", color = colors.brand, fontFamily = KidhackFontFamily, fontWeight = FontBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("취소", color = colors.muted, fontFamily = KidhackFontFamily, fontWeight = FontBold)
                }
            },
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = colors.brand,
                todayDateBorderColor = colors.brand,
            ),
        ) {
            DatePicker(
                state = pickerState,
                title = { Text(label, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = colors.brand,
                    todayDateBorderColor = colors.brand,
                ),
            )
        }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this, IsoDate) }.getOrNull()

private fun LocalDate.toDisplayDate(): String =
    "${year}년 ${monthValue}월 ${dayOfMonth}일"

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toIsoDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().format(IsoDate)
