package org.briarproject.briar.android.chatbot;

import android.util.Log;

import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
public class ChatbotService {

	private static final String TAG = ChatbotService.class.getName();

	public interface ChatbotCallback<T> {
		void onResult(T result);
		void onError(Exception e);
	}

	public void getSuggestions(String lastMessage, ChatbotCallback<List<String>> callback) {
		if (lastMessage == null || lastMessage.isEmpty()) {
			callback.onResult(Collections.emptyList());
			return;
		}

		List<TextMessage> conversation = new ArrayList<>();
		conversation.add(TextMessage.createForRemoteUser(
				lastMessage, System.currentTimeMillis(), "remote-user"));

		SmartReplyGenerator smartReply = SmartReply.getClient();
		smartReply.suggestReplies(conversation)
				.addOnSuccessListener(result -> {
					if (result.getStatus() == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
						callback.onResult(Collections.emptyList());
					} else if (result.getStatus() == SmartReplySuggestionResult.STATUS_SUCCESS) {
						List<String> suggestions = new ArrayList<>();
						for (SmartReplySuggestion suggestion : result.getSuggestions()) {
							suggestions.add(suggestion.getText());
						}
						callback.onResult(suggestions);
					} else {
						callback.onResult(Collections.emptyList());
					}
				})
				.addOnFailureListener(e -> {
					Log.e(TAG, "Smart Reply failed", e);
					callback.onError(e);
				});
	}

	public String checkGrammar(String text) {
		if (text == null || text.isEmpty()) return text;
		// Placeholder for ONNX implementation
		// In a real implementation, you would load the T5/DistilBERT model here via ONNX Runtime
		// and run inference to get the corrected text.
		return text;
	}

	public String changeStyle(String text, String style) {
		if (text == null || text.isEmpty()) return text;
		
		switch (style.toLowerCase()) {
			case "professional":
				return "I would like to inform you that: " + text;
			case "casual":
				return "Hey, " + text.toLowerCase();
			case "funny":
				return text + " (just kidding! 🤖)";
			default:
				return text;
		}
	}
}
