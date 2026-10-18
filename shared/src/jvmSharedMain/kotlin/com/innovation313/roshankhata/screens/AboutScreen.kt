package com.innovation313.roshankhata.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.res.Res
import com.innovation313.roshankhata.res.about_maker
import com.innovation313.roshankhata.res.about_privacy_body
import com.innovation313.roshankhata.res.about_privacy_policy
import com.innovation313.roshankhata.res.about_privacy_title
import com.innovation313.roshankhata.res.about_version
import com.innovation313.roshankhata.res.about_what
import com.innovation313.roshankhata.res.app_name
import org.jetbrains.compose.resources.stringResource

/**
 * About (the first shared screen, KMP Phase 5a): what this is, where the
 * data lives, and who is answerable for it — the same three things, in the
 * same order and sizes, as the XML screen it replaces. The privacy line sits
 * above the fold rather than behind a link: it is the question a shopkeeper
 * asks before trusting a ledger to a phone.
 *
 * Everything platform-shaped comes in: the app icon, the version, the
 * support address, what the policy button opens, and the debug taps on the
 * version line.
 */
@Composable
fun AboutScreen(
    icon: Painter,
    versionName: String,
    supportEmail: String,
    onPrivacyPolicy: () -> Unit,
    onVersionTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.pageBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.padding(top = 12.dp).size(72.dp).align(Alignment.CenterHorizontally)
        )
        Text(
            stringResource(Res.string.app_name),
            Modifier.fillMaxWidth().padding(top = 14.dp),
            color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        val version = stringResource(Res.string.about_version, versionName)
        Text(
            version,
            Modifier.fillMaxWidth().padding(top = 2.dp).testTag("aboutVersion")
                .let { m -> if (onVersionTap != null) m.clickable(onClick = onVersionTap) else m },
            color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Text(
            stringResource(Res.string.about_what),
            Modifier.fillMaxWidth().padding(top = 22.dp).testTag("aboutWhat"),
            color = c.inkSoft, fontSize = 15.sp, lineHeight = 15.sp * 1.6f
        )
        Card(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(Res.string.about_privacy_title), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(Res.string.about_privacy_body),
                    Modifier.padding(top = 8.dp),
                    color = c.inkSoft, fontSize = 14.sp, lineHeight = 14.sp * 1.55f
                )
                OutlinedButton(
                    onClick = onPrivacyPolicy,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp).testTag("aboutPolicy"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, c.brandGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.brandGreenText)
                ) {
                    Text(stringResource(Res.string.about_privacy_policy), fontSize = 14.sp)
                }
            }
        }
        Text(
            stringResource(Res.string.about_maker),
            Modifier.fillMaxWidth().padding(top = 24.dp),
            color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        SelectionContainer {
            Text(
                supportEmail,
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(0.dp))
    }
}
