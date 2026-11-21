package ru.krabikk.nntcschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun ChangelogDialog(
    version: ChangelogVersion,
    onDismiss: () -> Unit
) {

    AlertDialog(

        onDismissRequest =
            onDismiss,


        title = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    )
            ) {

                Text(
                    text =
                        version.title,

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall
                )


                Text(
                    text =
                        "Версия ${version.version}",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        },


        text = {

            LazyColumn(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {

                items(
                    items =
                        version.changes,

                    key = {
                        it
                    }
                ) { change ->

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                "•",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyLarge
                        )


                        Text(
                            text =
                                change,

                            modifier =
                                Modifier.padding(
                                    start = 8.dp
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyLarge
                        )
                    }
                }
            }
        },


        confirmButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    "Понятно"
                )
            }
        }
    )
}