package com.example.kronos.ui.exams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kronos.data.ExamRepository
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Exam
import com.example.kronos.databinding.FragmentExamsBinding
import com.example.kronos.notifications.TaskExamReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ExamsFragment : Fragment() {
    private var _binding: FragmentExamsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamViewModel by viewModels {
        val db = DatabaseProvider.get(requireContext())
        ExamViewModel.Factory(
            ExamRepository(db.examDao(), db.subjectDao()),
            TaskExamReminderScheduler(requireContext())
        )
    }
    private val upcomingAdapter = ExamAdapter(::editExam)
    private val pastAdapter = ExamAdapter(::editExam)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentExamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.upcomingList.layoutManager = LinearLayoutManager(requireContext())
        binding.pastList.layoutManager = LinearLayoutManager(requireContext())
        binding.upcomingList.adapter = upcomingAdapter
        binding.pastList.adapter = pastAdapter
        binding.addExam.setOnClickListener { openEditor(null) }
        binding.pastHeader.setOnClickListener {
            binding.pastList.visibility =
                if (binding.pastList.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        parentFragmentManager.setFragmentResultListener(
            ExamEditorBottomSheet.REQUEST, viewLifecycleOwner
        ) { _, result -> handleResult(result) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exams.collect { all ->
                    val today = LocalDate.now().toString()
                    upcomingAdapter.submitList(all.filter { it.date >= today })
                    pastAdapter.submitList(all.filter { it.date < today })
                    binding.pastHeader.visibility = if (pastAdapter.itemCount == 0) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun openEditor(exam: Exam?) {
        ExamEditorBottomSheet.new(exam, viewModel.subjects.value)
            .show(parentFragmentManager, "exam_editor")
    }

    private fun editExam(item: com.example.kronos.data.local.ExamWithSubject) {
        viewLifecycleOwner.lifecycleScope.launch { openEditor(viewModel.find(item.id)) }
    }

    private fun handleResult(result: Bundle) {
        val id = result.getLong(ExamEditorBottomSheet.RESULT_ID)
        if (result.getBoolean(ExamEditorBottomSheet.RESULT_DELETE)) {
            viewLifecycleOwner.lifecycleScope.launch { viewModel.find(id)?.let(viewModel::delete) }
            return
        }
        viewModel.save(
            Exam(
                id = id,
                title = viewModel.subjects.value.firstOrNull {
                    it.id == result.getLong(ExamEditorBottomSheet.RESULT_SUBJECT_ID)
                }?.name ?: "Exam",
                subjectId = result.getLong(ExamEditorBottomSheet.RESULT_SUBJECT_ID).takeUnless { it == 0L },
                date = LocalDate.parse(result.getString(ExamEditorBottomSheet.RESULT_DATE)),
                startTime = result.getString(ExamEditorBottomSheet.RESULT_TIME)?.takeIf { it.isNotBlank() }?.let(LocalTime::parse),
                room = result.getString(ExamEditorBottomSheet.RESULT_ROOM)?.takeIf { it.isNotBlank() },
                seat = result.getString(ExamEditorBottomSheet.RESULT_SEAT)?.takeIf { it.isNotBlank() },
                notes = result.getString(ExamEditorBottomSheet.RESULT_NOTES)?.takeIf { it.isNotBlank() }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
