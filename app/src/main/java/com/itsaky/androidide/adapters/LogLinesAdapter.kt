/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.adapters

import android.annotation.SuppressLint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.Callback
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.SizeUtils
import com.blankj.utilcode.util.ThreadUtils
import com.itsaky.androidide.adapters.LogLinesAdapter.VH
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.ILogger.Priority
import com.itsaky.androidide.utils.ILogger.Priority.ERROR
import com.itsaky.androidide.utils.ILogger.Priority.INFO
import com.itsaky.androidide.utils.ILogger.Priority.VERBOSE
import com.itsaky.androidide.utils.ILogger.Priority.WARNING
import com.itsaky.androidide.utils.TypefaceUtils
import java.util.concurrent.*

/**
 * Adapter for the log view.
 * @author Akash Yadav
 */
class LogLinesAdapter : RecyclerView.Adapter<VH>() {

    private val log = ILogger.newInstance(javaClass.simpleName)
    private val lines: MutableList<LogWrapper> = mutableListOf()
    private var shown = lines
    private var isFiltered = false
    private var query: Query? = null
    var simpleFormatting = true

    companion object {
        const val MAX_LINES = 5 * 1000
    }

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        this.lines.clear()
        this.shown.clear()
        this.isFiltered = false
        this.query = null
        this.notifyDataSetChanged()
    }

    fun add(line: LogLine) {
        val wrapped = wrap(line)
        this.lines.add(wrapped)
        if (isFiltered && matchesFilter(wrapped, query!!.query, query!!.priority)) {
            shown.add(wrapped)
            notifyItemInserted(this.shown.lastIndex)
        } else {
            notifyItemInserted(this.lines.lastIndex)
        }

        if (this.lines.size > MAX_LINES) {
            this.lines.removeAt(0)
            if (isFiltered && this.shown[0] == this.lines[0]) {
                this.shown.removeAt(0)
            }
            this.notifyItemRemoved(0)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String, priority: Priority) {
        this.query = Query(query, priority)
        CompletableFuture.supplyAsync {
                val filtered = this.lines.filter { matchesFilter(it, query, priority) }
                val diffResult =
                    DiffUtil.calculateDiff(
                        object : Callback() {
                            override fun getOldListSize(): Int {
                                return lines.size
                            }

                            override fun getNewListSize(): Int {
                                return filtered.size
                            }

                            override fun areItemsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int
                            ): Boolean {
                                return lines[oldItemPosition] == filtered[newItemPosition]
                            }

                            override fun areContentsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int
                            ): Boolean {
                                return areItemsTheSame(oldItemPosition, newItemPosition)
                            }
                        })
                return@supplyAsync filtered to diffResult
            }
            .whenComplete { result, error ->
                if (error != null) {
                    log.error("An error occurred while filtering logs", error)
                    return@whenComplete
                }

                shown = result.first.toMutableList()
                isFiltered = shown.size != lines.size
                ThreadUtils.runOnUiThread {
                    if (!isFiltered) {
                        this.shown = lines
                        this.query = null
                        notifyDataSetChanged()
                    } else {
                        result.second.dispatchUpdatesTo(this)
                    }
                }
            }
    }

    fun matchesFilter(wrapper: LogWrapper, query: String, priority: Priority): Boolean {
        val line = wrapper.log
        return (query.isBlank() || lineString(line).contains(query, ignoreCase = true)) &&
            (line.priority == priority || priority == VERBOSE)
    }

    fun getLine(index: Int) = this.lines[index].log

    fun allAsString() =
        StringBuilder()
            .apply {
                for (line in lines) {
                    append(lineString(line))
                    append("\n")
                }
            }
            .toString()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val dp1 = SizeUtils.dp2px(1f)
        val text = TextView(parent.context)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        text.setPaddingRelative(dp1, dp1 / 2, dp1, dp1 / 2)
        text.setTextIsSelectable(true)
        text.maxLines = 1
        text.typeface = TypefaceUtils.jetbrainsMono()
        return VH(text)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = shown[position]
        if (!line.visible) {
            holder.itemView.visibility = View.GONE
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
        }

        val spanned = line.spannable
        holder.text.text = spanned
    }

    override fun getItemCount() = this.shown.size

    private fun wrap(line: LogLine): LogWrapper {
        val spannable = SpannableString(lineString(line))
        val color =
            when (line.priority) {
                ERROR -> -0xbbcca
                WARNING -> -0x14c5
                INFO -> -0xb350b0
                else -> -0xa0a0b
            }
        spannable.setSpan(
            ForegroundColorSpan(color), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return LogWrapper(line, spannable).apply { visible = true }
    }

    private fun lineString(logLine: LogLine) =
        if (simpleFormatting) {
            logLine.toSimpleString()
        } else {
            logLine.toString()
        }

    class LogWrapper(val log: LogLine, val spannable: Spannable) : LogLine() {

        var visible = true

        override fun toString() = this.log.toString()
        override fun toSimpleString(): String = this.log.toSimpleString()
        override fun formattedTagAndMessage(): String = this.log.formattedTagAndMessage()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LogWrapper

            if (log != other.log) return false
            if (spannable != other.spannable) return false
            if (visible != other.visible) return false

            return true
        }

        override fun hashCode(): Int {
            var result = log.hashCode()
            result = 31 * result + spannable.hashCode()
            result = 31 * result + visible.hashCode()
            return result
        }
    }

    data class Query(val query: String, val priority: Priority)
}
