package com.aiagent.assistant.presentation.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Assistant-এর উত্তর Markdown হিসেবে সাজিয়ে দেখায়। Code block-এ নিজস্ব Copy বোতাম আছে। */
@Composable
fun MarkdownText(
    markdown: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = LocalContentColor.current
) {
    val cs = MaterialTheme.colorScheme
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = cs.primary.copy(alpha = 0.18f),
        color = textColor
    )
    val linkStyle = SpanStyle(
        color = cs.secondary,
        textDecoration = TextDecoration.Underline
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val base = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = MarkdownParser.parseInline(block.text, codeStyle, linkStyle),
                        style = base.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = MarkdownParser.parseInline(block.text, codeStyle, linkStyle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                is MdBlock.Bullet -> Row(
                    modifier = Modifier.padding(start = (block.indent * 16).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "•", style = MaterialTheme.typography.bodyLarge, color = textColor)
                    Text(
                        text = MarkdownParser.parseInline(block.text, codeStyle, linkStyle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Numbered -> Row(
                    modifier = Modifier.padding(start = (block.indent * 16).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = block.number + ".", style = MaterialTheme.typography.bodyLarge, color = textColor)
                    Text(
                        text = MarkdownParser.parseInline(block.text, codeStyle, linkStyle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Quote -> Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(cs.primary)
                    )
                    Text(
                        text = MarkdownParser.parseInline(block.text, codeStyle, linkStyle),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = textColor.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Code -> CodeBlock(
                    language = block.language,
                    code = block.code,
                    onCopy = onCopyCode
                )
                MdBlock.Rule -> HorizontalDivider(color = cs.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    onCopy: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceContainerLowest)
            .border(1.dp, cs.outline.copy(alpha = 0.3f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onCopy(code) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = cs.secondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Copy · কপি", color = cs.secondary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = code,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            color = cs.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}
