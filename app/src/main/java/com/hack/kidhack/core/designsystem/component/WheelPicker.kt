package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** 한 줄 높이. 가운데 강조선 간격과 스크롤 계산이 모두 이 값을 기준으로 한다. */
private val ITEM_HEIGHT = 44.dp

/** 숫자 오른쪽 단위 글자가 차지하는 폭. 좌우 같은 폭을 비워 숫자를 가운데 고정한다. */
private val LABEL_SLOT = 34.dp

/** 위아래로 보이는 여백 줄 수. 2 면 가운데 1 줄 + 위아래 2 줄씩 = 5 줄이 보인다. */
private const val EDGE_ITEMS = 2

/**
 * 숫자 하나를 고르는 휠.
 *
 * <p>시간·분처럼 값이 촘촘한 설정에서 키보드로 숫자를 치게 하면 오타가 나기 쉽고, 자녀
 * 기기를 잠그는 값이라 잘못 입력되면 곧바로 문제가 된다. 굴려서 고르면 잘못 넣을 수 있는
 * 값 자체가 없다.
 *
 * <p>가운데 칸에 멈추도록 스냅을 걸고, 멈춘 뒤에만 [onValueChange] 를 부른다. 굴리는
 * 도중까지 알리면 저장 대상 값이 계속 흔들린다.
 */
@Composable
fun WheelPicker(
    values: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    /** 가운데 강조선을 이 휠이 직접 그릴지. 여러 휠을 나란히 둘 때는 밖에서 한 줄로 긋는다. */
    showGuides: Boolean = true,
) {
    val colors = LocalKidhackColors.current
    val startIndex = remember(values, value) { values.indexOf(value).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    // 위아래 여백만큼 안쪽으로 밀어 두면 firstVisibleItemIndex 가 곧 가운데 칸이 된다.
    val edgePadding = ITEM_HEIGHT * EDGE_ITEMS

    // 굴림이 멈춘 뒤에만 알린다.
    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, index) -> index }
            .distinctUntilChanged()
            .collect { index -> values.getOrNull(index)?.let(onValueChange) }
    }

    // 밖에서 값이 바뀌면(예: 저장 후 되돌림) 휠도 따라간다.
    LaunchedEffect(value) {
        val target = values.indexOf(value)
        if (target >= 0 && target != listState.firstVisibleItemIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(target)
        }
    }

    Box(modifier = modifier.height(ITEM_HEIGHT * (EDGE_ITEMS * 2 + 1))) {
        // 가운데 칸을 알려 주는 두 줄. 선택된 값이 어디인지 눈으로 잡아 준다.
        if (showGuides) {
            WheelGuides(Modifier.align(Alignment.Center))
        }

        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = edgePadding),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(values.size) { index ->
                val selected = index == listState.firstVisibleItemIndex
                Row(
                    modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // 단위는 오른쪽에만 붙지만 자리는 양쪽에 똑같이 비워 둔다. 한쪽만 두면
                    // 가운데 줄에 단위가 나타나는 순간 숫자가 왼쪽으로 밀려 흔들려 보인다.
                    if (label != null) Spacer(Modifier.width(LABEL_SLOT))
                    Text(
                        text = "%02d".format(values[index]),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = if (selected) 26.sp else 18.sp,
                        color = if (selected) colors.text else colors.muted,
                        textAlign = TextAlign.Center,
                    )
                    if (label != null) {
                        Box(Modifier.width(LABEL_SLOT)) {
                            if (selected) {
                                Text(
                                    text = label,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 6.dp),
                                    color = colors.muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 가운데 칸을 감싸는 두 줄. 휠을 나란히 놓을 때 밖에서 한 번만 그리려고 따로 뒀다. */
@Composable
fun WheelGuides(modifier: Modifier = Modifier) {
    val colors = LocalKidhackColors.current
    Box(modifier.fillMaxWidth().height(ITEM_HEIGHT)) {
        HorizontalDivider(Modifier.align(Alignment.TopCenter), color = colors.border)
        HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colors.border)
    }
}

/** 휠 한 줄 높이. 여러 휠을 겹쳐 놓는 쪽에서 강조선 위치를 맞출 때 쓴다. */
val WheelItemHeight: androidx.compose.ui.unit.Dp get() = ITEM_HEIGHT
