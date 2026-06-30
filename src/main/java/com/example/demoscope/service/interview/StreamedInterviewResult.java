package com.example.demoscope.service.interview;

import com.example.demoscope.domain.interview.InterviewSnapshot;

public record StreamedInterviewResult(String text, InterviewSnapshot snapshot) {
}
