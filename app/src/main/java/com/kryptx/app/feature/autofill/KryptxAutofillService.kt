package com.kryptx.app.feature.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.kryptx.app.MainActivity

class KryptxAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val context = request.fillContexts.lastOrNull() ?: return
        val structure = context.structure

        val autofillIds = mutableListOf<AutofillId>()
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            traverseNode(rootNode, autofillIds)
        }

        if (autofillIds.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_target", "search")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "Unlock Kryptx to Autofill")
        }

        val fillResponse = FillResponse.Builder()
            .setAuthentication(
                autofillIds.toTypedArray(),
                pendingIntent.intentSender,
                presentation
            )
            .build()

        callback.onSuccess(fillResponse)
    }

    private fun traverseNode(node: AssistStructure.ViewNode, autofillIds: MutableList<AutofillId>) {
        if (node.autofillId != null) {
            autofillIds.add(node.autofillId!!)
        }
        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), autofillIds)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
