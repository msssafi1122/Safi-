package com.example;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final Context context;
    private final List<ChatMessage> messageList;

    public ChatAdapter(Context context, List<ChatMessage> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_ai, parent, false);
            return new AiMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messageList.get(position);
        if (getItemViewType(position) == VIEW_TYPE_USER) {
            ((UserMessageViewHolder) holder).tvMessageText.setText(msg.text);
        } else {
            AiMessageViewHolder aiHolder = (AiMessageViewHolder) holder;
            aiHolder.tvMessageText.setText(msg.text);
            aiHolder.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("SAFI_AI_Message", msg.text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Copied response to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessageText;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tvChatMessageText);
        }
    }

    public static class AiMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessageText;
        Button btnCopy;

        public AiMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tvChatMessageText);
            btnCopy = itemView.findViewById(R.id.btnCopyAiText);
        }
    }

    public static class ChatMessage {
        public String text;
        public boolean isUser;

        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }
}
