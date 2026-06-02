package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import org.briarproject.briar.R;
import org.briarproject.briar.android.chatbot.ChatbotService;
import org.briarproject.briar.android.view.EmojiTextInputView.OnKeyboardShownListener;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.List;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static java.util.Objects.requireNonNull;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TextInputView extends LinearLayout {

	@Nullable
	TextSendController textSendController;
	final EmojiTextInputView textInput;
	private final ChatbotService chatbotService = new ChatbotService();
	private String lastIncomingMessage = "";

	public TextInputView(Context context) {
		this(context, null);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setSaveEnabled(true);
		setOrientation(VERTICAL);
		setLayoutTransition(new LayoutTransition());

		// inflate layout
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(getLayout(), this, true);

		// get attributes
		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.TextInputView);
		String hint = attributes.getString(R.styleable.TextInputView_hint);
		boolean allowEmptyText = attributes
				.getBoolean(R.styleable.TextInputView_allowEmptyText, false);
		attributes.recycle();

		textInput = findViewById(R.id.emojiTextInput);
		textInput.setAllowEmptyText(allowEmptyText);
		if (hint != null) textInput.setHint(hint);

		ImageButton chatbotButton = findViewById(R.id.chatbotButton);
		if (chatbotButton != null) {
			chatbotButton.setOnClickListener(this::showChatbotMenu);
		}
	}

	public void setLastIncomingMessage(String message) {
		this.lastIncomingMessage = message;
	}

	private void showChatbotMenu(View v) {
		PopupMenu popup = new PopupMenu(getContext(), v);
		popup.getMenuInflater().inflate(R.menu.chatbot_menu, popup.getMenu());
		popup.setOnMenuItemClickListener(item -> {
			int id = item.getItemId();
			if (id == R.id.suggest_reply) {
				chatbotService.getSuggestions(lastIncomingMessage, new ChatbotService.ChatbotCallback<List<String>>() {
					@Override
					public void onResult(List<String> suggestions) {
						showSuggestionsDialog(suggestions);
					}

					@Override
					public void onError(Exception e) {
						// Handle error
					}
				});
				return true;
			} else if (id == R.id.check_grammar) {
				String corrected = chatbotService.checkGrammar(textInput.getText());
				textInput.setText(corrected);
				return true;
			} else if (id == R.id.style_professional) {
				String styled = chatbotService.changeStyle(textInput.getText(), "professional");
				textInput.setText(styled);
				return true;
			} else if (id == R.id.style_casual) {
				String styled = chatbotService.changeStyle(textInput.getText(), "casual");
				textInput.setText(styled);
				return true;
			} else if (id == R.id.style_funny) {
				String styled = chatbotService.changeStyle(textInput.getText(), "funny");
				textInput.setText(styled);
				return true;
			}
			return false;
		});
		popup.show();
	}

	private void showSuggestionsDialog(List<String> suggestions) {
		String[] items = suggestions.toArray(new String[0]);
		new androidx.appcompat.app.AlertDialog.Builder(getContext(), R.style.BriarDialogTheme)
				.setTitle(R.string.suggest_reply)
				.setItems(items, (dialog, which) -> {
					textInput.setText(items[which]);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@LayoutRes
	protected int getLayout() {
		return R.layout.text_input_view;
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		if (textSendController != null) {
			superState = textSendController.onSaveInstanceState(superState);
		}
		return superState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (textSendController != null) {
			Parcelable outState =
					textSendController.onRestoreInstanceState(state);
			super.onRestoreInstanceState(outState);
		} else {
			super.onRestoreInstanceState(state);
		}
	}

	/**
	 * Call this in onCreate() before any other methods of this class.
	 */
	public <T extends TextSendController> void setSendController(T controller) {
		textSendController = controller;
		textInput.setTextInputListener(textSendController);
	}

	@Override
	public void setEnabled(boolean enabled) {
		throw new RuntimeException("Use controllers to enable/disable");
	}

	public void setReady(boolean ready) {
		requireNonNull(textSendController).setReady(ready);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return textInput.requestFocus(direction, previouslyFocusedRect);
	}

	EmojiTextInputView getEmojiTextInputView() {
		return textInput;
	}

	public void clearText() {
		textInput.clearText();
	}

	public void setHint(@StringRes int res) {
		textInput.setHint(getContext().getString(res));
	}

	public void setMaxTextLength(int maxLength) {
		textInput.setMaxLength(maxLength);
	}

	public boolean isKeyboardOpen() {
		return textInput.isKeyboardOpen();
	}

	public void showSoftKeyboard() {
		textInput.showSoftKeyboard();
	}

	public void hideSoftKeyboard() {
		textInput.hideSoftKeyboard();
	}

	public void setOnKeyboardShownListener(
			@Nullable OnKeyboardShownListener listener) {
		textInput.setOnKeyboardShownListener(listener);
	}

}
