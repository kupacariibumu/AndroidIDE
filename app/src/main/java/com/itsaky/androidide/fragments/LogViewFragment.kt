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

package com.itsaky.androidide.fragments

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.itsaky.androidide.adapters.LogLinesAdapter
import com.itsaky.androidide.databinding.FragmentLogBinding
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.ILogger.Priority
import com.itsaky.androidide.utils.SingleTextWatcher

/**
 * Fragment to show logs in a [androidx.recyclerview.widget.RecyclerView].
 * @author Akash Yadav
 */
abstract class LogViewFragment : Fragment() {

    private val log = ILogger.newInstance(javaClass.simpleName)
    var binding: FragmentLogBinding? = null
    var adapter: LogLinesAdapter? = null

    fun appendLog(line: LogLine) {
        if (this.binding == null || this.adapter == null) {
            return
        }

        this.binding!!.lines.post {
            this.adapter!!.simpleFormatting = isSimpleFormattingEnabled()
            this.adapter!!.add(line)
        }
    }

    abstract fun getLogType(): String

    abstract fun isSimpleFormattingEnabled(): Boolean

    protected open fun logLine(priority: Priority, tag: String, message: String) {
        val line = LogLine(priority, tag, message)
        appendLog(line)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val manager = LinearLayoutManager(requireContext())
        this.binding!!.lines.layoutManager = manager

        this.adapter = LogLinesAdapter()
        this.binding!!.lines.adapter = this.adapter

        this.binding!!
            .searchField
            .addTextChangedListener(
                object : SingleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        val text = s.toString().trim()
                        val priority =
                            Priority.values()[binding!!.priorityFilter.selectedItemPosition]
                        adapter!!.filter(text, priority)
                    }
                })

        this.binding!!.priorityFilter.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                Priority.values().map { it.name })
        this.binding!!.priorityFilter.onItemSelectedListener =
            object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val text = binding!!.searchField.text.toString().trim()
                    val priority = Priority.values()[position]
                    adapter!!.filter(text, priority)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.binding = null
        this.adapter = null
    }
}
