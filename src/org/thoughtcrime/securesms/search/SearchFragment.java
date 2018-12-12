package org.thoughtcrime.securesms.search;

import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.b44t.messenger.DcChatlist;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcMsg;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.search.model.SearchResult;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;

import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * A fragment that is displayed to do full-text search of messages, groups, and contacts.
 */
public class SearchFragment extends Fragment implements SearchListAdapter.EventListener {

  public static final String TAG          = "SearchFragment";
  public static final String EXTRA_LOCALE = "locale";

  private TextView               noResultsView;
  private RecyclerView           listView;
  private StickyHeaderDecoration listDecoration;

  private SearchViewModel   viewModel;
  private SearchListAdapter listAdapter;
  private String            pendingQuery;
  private Locale            locale;

  public static SearchFragment newInstance(@NonNull Locale locale) {
    Bundle args = new Bundle();
    args.putSerializable(EXTRA_LOCALE, locale);

    SearchFragment fragment = new SearchFragment();
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    this.locale = (Locale) getArguments().getSerializable(EXTRA_LOCALE);

    SearchRepository searchRepository = new SearchRepository(getContext(),
                                                             Executors.newSingleThreadExecutor());
    viewModel = ViewModelProviders.of(this, new SearchViewModel.Factory(searchRepository)).get(SearchViewModel.class);

    if (pendingQuery != null) {
      viewModel.updateQuery(pendingQuery);
      pendingQuery = null;
    }
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_search, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    noResultsView = view.findViewById(R.id.search_no_results);
    listView      = view.findViewById(R.id.search_list);

    listAdapter    = new SearchListAdapter(getContext(), GlideApp.with(this), this, locale);
    listDecoration = new StickyHeaderDecoration(listAdapter, false, false);

    listView.setAdapter(listAdapter);
    listView.addItemDecoration(listDecoration);
    listView.setLayoutManager(new LinearLayoutManager(getContext()));
  }

  @Override
  public void onStart() {
    super.onStart();
    viewModel.getSearchResult().observe(this, result -> {
      result = result != null ? result : SearchResult.EMPTY;

      listAdapter.updateResults(result);

      if (result.isEmpty()) {
        if (TextUtils.isEmpty(viewModel.getLastQuery().trim())) {
          noResultsView.setVisibility(View.GONE);
        } else {
          noResultsView.setVisibility(View.VISIBLE);
          noResultsView.setText(getString(R.string.search_no_result_for_x, viewModel.getLastQuery()));
        }
      } else {
        noResultsView.setVisibility(View.VISIBLE);
        noResultsView.setText("");
      }
    });
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (listDecoration != null) {
      listDecoration.invalidateLayouts();
    }
  }


  @Override
  public void onConversationClicked(@NonNull DcChatlist.Item chatlistItem) {
    ConversationListActivity conversationList = (ConversationListActivity) getActivity();
    if (conversationList != null) {
      conversationList.onCreateConversation(chatlistItem.chatId,0);
    }
  }

  @Override
  public void onContactClicked(@NonNull DcContact contact) {
    ConversationListActivity conversationList = (ConversationListActivity) getActivity();
    if (conversationList != null) {
      ApplicationDcContext dcContext = DcHelper.getContext(getContext());
      int chatId = dcContext.getChatIdByContactId(contact.getId());
      if(chatId==0) {
        new AlertDialog.Builder(getContext())
            .setMessage(getString(R.string.ask_start_chat_with, contact.getNameNAddr()))
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
              int chatId1 = dcContext.createChatByContactId(contact.getId());
              conversationList.onCreateConversation(chatId1,0);
            }).show();
      }
      else {
        conversationList.onCreateConversation(chatId,0);
      }
    }
  }

  @Override
  public void onMessageClicked(@NonNull DcMsg message) {
    ConversationListActivity conversationList = (ConversationListActivity) getActivity();
    if (conversationList != null) {
      ApplicationDcContext dcContext = DcHelper.getContext(getContext());
      int startingPosition = -1;
      int chatId = message.getChatId();
      int msgId = message.getId();
      int msgs[] = dcContext.getChatMsgs(chatId, 0, 0);
      for(int i=0; i<msgs.length; i++ ) {
        if(msgs[i]==msgId) {
          startingPosition = msgs.length-1-i;
          break;
        }
      }
      conversationList.openConversation(chatId, 0, startingPosition);
    }
  }

  public void updateSearchQuery(@NonNull String query) {
    if (viewModel != null) {
      viewModel.updateQuery(query);
    } else {
      pendingQuery = query;
    }
  }
}
