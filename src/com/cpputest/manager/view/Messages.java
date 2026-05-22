package com.cpputest.manager.view;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "com.cpputest.manager.view.messages";

    // ツールバーのアクションラベル
    public static String action_generate;
    public static String action_setting;
    public static String action_scan;
    public static String action_clear_all;

    // ツールチップ
    public static String tooltip_scan;
    public static String tooltip_setting;
    public static String tooltip_generate;
    public static String tooltip_clear;

    // ラベル
    public static String label_project_none;
    public static String label_project_format;

    // ダイアログ
    public static String dialog_generate_title;
    public static String dialog_generate_message;
    public static String dialog_setting_title;
    public static String dialog_setting_message;
    public static String dialog_success_title;
    public static String dialog_success_message;
    public static String dialog_error_title;
    public static String dialog_error_message;

    // ツリービューアの列ヘッダ
    public static String column_test_group_name;
    public static String column_status;

    // ステータス文字列
    public static String status_success;
    public static String status_failure;
    public static String status_idle;

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {}
}
