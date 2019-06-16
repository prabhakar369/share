package org.odk.share.views.ui.instance.fragment;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.share.R;
import org.odk.share.views.ui.common.InstanceListFragment;
import org.odk.share.views.ui.hotspot.HpSenderActivity;
import org.odk.share.views.ui.instance.adapter.TransferInstanceAdapter;
import org.odk.share.dao.TransferDao;
import org.odk.share.dto.TransferInstance;
import org.odk.share.views.listeners.OnItemClickListener;
import org.odk.share.utilities.ApplicationConstants;
import org.odk.share.utilities.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static org.odk.share.views.ui.instance.InstancesList.INSTANCE_IDS;
import static org.odk.share.views.ui.main.MainActivity.FORM_DISPLAY_NAME;
import static org.odk.share.views.ui.main.MainActivity.FORM_ID;
import static org.odk.share.views.ui.main.MainActivity.FORM_VERSION;

/**
 * Created by laksh on 6/27/2018.
 */

public class ReviewedInstancesFragment extends InstanceListFragment implements OnItemClickListener {

    public static final String MODE = "mode";
    private static final String REVIEWED_INSTANCE_LIST_SORTING_ORDER = "reviewedInstanceListSortingOrder";

    @BindView(R.id.recyclerview)
    RecyclerView recyclerView;
    @BindView(R.id.bToggle)
    Button toggleButton;
    @BindView(R.id.bAction)
    Button sendButton;
    @BindView(R.id.empty_view)
    TextView emptyView;
    @BindView(R.id.buttonholder)
    LinearLayout buttonLayout;

    @Inject
    InstancesDao instancesDao;

    @Inject
    TransferDao transferDao;

    HashMap<Long, Instance> instanceMap;
    TransferInstanceAdapter transferInstanceAdapter;
    List<TransferInstance> transferInstanceList;

    public ReviewedInstancesFragment() {

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_instances, container, false);
        ButterKnife.bind(this, view);

        instanceMap = new HashMap<>();
        transferInstanceList = new ArrayList<>();
        selectedInstances = new LinkedHashSet<>();
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(llm);

        toggleButton.setText(getString(R.string.select_all));
        sendButton.setText(getString(R.string.send_forms));

        setupAdapter();
        return view;
    }

    @Override
    protected void updateAdapter() {
        getInstanceFromDB();
        setEmptyViewVisibility(getString(R.string.no_forms_reviewed,
                getActivity().getIntent().getStringExtra(FORM_DISPLAY_NAME)));
        transferInstanceAdapter.notifyDataSetChanged();
    }

    @Override
    protected String getSortingOrderKey() {
        return REVIEWED_INSTANCE_LIST_SORTING_ORDER;
    }

    @Override
    public void onResume() {
        getInstanceFromDB();

        setEmptyViewVisibility(getString(R.string.no_forms_reviewed,
                getActivity().getIntent().getStringExtra(FORM_DISPLAY_NAME)));
        transferInstanceAdapter.notifyDataSetChanged();
        super.onResume();
    }

    private void getInstanceFromDB() {
        transferInstanceList.clear();
        selectedInstances.clear();
        String formVersion = getActivity().getIntent().getStringExtra(FORM_VERSION);
        String formId = getActivity().getIntent().getStringExtra(FORM_ID);
        String[] selectionArgs;
        String selection;

        if (formVersion == null) {
            selection = InstanceProviderAPI.InstanceColumns.JR_FORM_ID + "=? AND "
                    + InstanceProviderAPI.InstanceColumns.JR_VERSION + " IS NULL";
            if (getFilterText().length() == 0) {
                selectionArgs = new String[]{formId};
            } else {
                selectionArgs = new String[]{formId, "%" + getFilterText() + "%"};
                selection = "AND " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + " LIKE ?";
            }
        } else {
            selection = InstanceProviderAPI.InstanceColumns.JR_FORM_ID + "=? AND "
                    + InstanceProviderAPI.InstanceColumns.JR_VERSION + "=?";
            if (getFilterText().length() == 0) {
                selectionArgs = new String[]{formId, formVersion};
            } else {
                selectionArgs = new String[]{formId, "%" + getFilterText() + "%"};
                selection = "AND " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + " LIKE ?";
            }
        }

        Cursor cursor = instancesDao.getInstancesCursor(null, selection, selectionArgs, getSortingOrder());
        instanceMap = instancesDao.getMapFromCursor(cursor);

        Cursor transferCursor = transferDao.getReviewedInstancesCursor();
        List<TransferInstance> transferInstances = transferDao.getInstancesFromCursor(transferCursor);
        for (TransferInstance instance : transferInstances) {
            if (instanceMap.containsKey(instance.getInstanceId())) {
                instance.setInstance(instanceMap.get(instance.getInstanceId()));
                transferInstanceList.add(instance);
            }
        }
    }

    private void setupAdapter() {
        transferInstanceAdapter = new TransferInstanceAdapter(getActivity(), transferInstanceList, this, selectedInstances, true);
        recyclerView.setAdapter(transferInstanceAdapter);
    }

    private void setEmptyViewVisibility(String text) {
        if (transferInstanceList.size() > 0) {
            buttonLayout.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        } else {
            buttonLayout.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(text);
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        CheckBox checkBox = view.findViewById(R.id.checkbox);
        checkBox.setChecked(!checkBox.isChecked());

        TransferInstance transferInstance = transferInstanceList.get(position);
        Long id = transferInstance.getId();

        if (selectedInstances.contains(id)) {
            selectedInstances.remove(id);
        } else {
            selectedInstances.add(id);
        }
        toggleButtonLabel();
    }

    @OnClick(R.id.bToggle)
    public void toggle() {
        boolean newState = transferInstanceAdapter.getItemCount() > selectedInstances.size();

        if (newState) {
            for (TransferInstance instance : transferInstanceList) {
                selectedInstances.add(instance.getId());
            }
        } else {
            selectedInstances.clear();
        }

        transferInstanceAdapter.notifyDataSetChanged();
        toggleButtonLabel();
    }

    private void toggleButtonLabel() {
        toggleButton.setText(selectedInstances.size() == transferInstanceAdapter.getItemCount() ?
                getString(R.string.clear_all) : getString(R.string.select_all));
        sendButton.setEnabled(selectedInstances.size() > 0);
    }

    @OnClick(R.id.bAction)
    public void sendForms() {
        List<Long> instanceIds = new ArrayList<>();
        for (TransferInstance transferInstance : transferInstanceList) {
            if (selectedInstances.contains(transferInstance.getId())) {
                instanceIds.add(transferInstance.getInstanceId());
            }
        }
        Intent intent = new Intent(getContext(), HpSenderActivity.class);
        Long[] arr = instanceIds.toArray(new Long[instanceIds.size()]);
        long[] a = ArrayUtils.toPrimitive(arr);
        intent.putExtra(INSTANCE_IDS, a);
        intent.putExtra(MODE, ApplicationConstants.SEND_REVIEW_MODE);
        startActivity(intent);
    }
}
