package org.wlf.filedownloader.file_move;

import android.util.Log;

import org.wlf.filedownloader.DownloadFileInfo;
import org.wlf.filedownloader.listener.OnMoveDownloadFileListener;
import org.wlf.filedownloader.listener.OnMoveDownloadFileListener.MoveDownloadFileFailReason;
import org.wlf.filedownloader.listener.OnMoveDownloadFileListener.OnMoveDownloadFileFailReason;
import org.wlf.filedownloader.util.DownloadFileUtil;
import org.wlf.filedownloader.util.FileUtil;

import java.io.File;

/**
 * move download file
 * <br/>
 * 移动下载文件任务
 *
 * @author wlf(Andy)
 * @email 411086563@qq.com
 */
class MoveDownloadFileTask implements Runnable {

    private static final String TAG = MoveDownloadFileTask.class.getSimpleName();

    private String mUrl;
    private String mNewDirPath;
    private DownloadFileMover mDownloadFileMover;
    private boolean mIsSyncCallback = false;

    private OnMoveDownloadFileListener mOnMoveDownloadFileListener;

    public MoveDownloadFileTask(String url, String newDirPath, DownloadFileMover downloadFileMover) {
        super();
        this.mUrl = url;
        this.mNewDirPath = newDirPath;
        this.mDownloadFileMover = downloadFileMover;
    }

    /**
     * set MoveDownloadFileListener
     *
     * @param onMoveDownloadFileListener MoveDownloadFileListener
     */
    public void setOnMoveDownloadFileListener(OnMoveDownloadFileListener onMoveDownloadFileListener) {
        this.mOnMoveDownloadFileListener = onMoveDownloadFileListener;
    }

    /**
     * enable the callback sync
     */
    public void enableSyncCallback() {
        mIsSyncCallback = true;
    }

    @Override
    public void run() {

        DownloadFileInfo downloadFileInfo = null;
        MoveDownloadFileFailReason failReason = null;

        try {
            downloadFileInfo = mDownloadFileMover.getDownloadFile(mUrl);

            // check null
            if (downloadFileInfo == null) {
                //                failReason = new MoveDownloadFileFailReason("the DownloadFile is empty!", 
                // MoveDownloadFileFailReason
                //                        .TYPE_NULL_POINTER);

                failReason = new OnMoveDownloadFileFailReason("the DownloadFile is empty!", 
                        OnMoveDownloadFileFailReason.TYPE_NULL_POINTER);
                // goto finally,notifyFailed()
                return;
            }

            // 1.prepared
            notifyPrepared(downloadFileInfo);

            // check status
            if (!DownloadFileUtil.canMove(downloadFileInfo)) {
                //                failReason = new MoveDownloadFileFailReason("the download file status error!", 
                //                        MoveDownloadFileFailReason.TYPE_FILE_STATUS_ERROR);

                failReason = new OnMoveDownloadFileFailReason("the download file status error!", 
                        OnMoveDownloadFileFailReason.TYPE_FILE_STATUS_ERROR);
                // goto finally,notifyFailed()
                return;
            }

            // check status
            File oldFile = null;
            File newFile = null;

            if (DownloadFileUtil.isCompleted(downloadFileInfo)) {
                oldFile = new File(downloadFileInfo.getFileDir(), downloadFileInfo.getFileName());
                newFile = new File(mNewDirPath, downloadFileInfo.getFileName());
            } else {
                oldFile = new File(downloadFileInfo.getFileDir(), downloadFileInfo.getTempFileName());
                newFile = new File(mNewDirPath, downloadFileInfo.getTempFileName());
            }

            // check original file
            if (oldFile == null || !oldFile.exists()) {
                //                failReason = new MoveDownloadFileFailReason("the original file does not exist!", 
                //                        MoveDownloadFileFailReason.TYPE_ORIGINAL_FILE_NOT_EXIST);

                failReason = new OnMoveDownloadFileFailReason("the original file does not exist!", 
                        OnMoveDownloadFileFailReason.TYPE_ORIGINAL_FILE_NOT_EXIST);
                // goto finally,notifyFailed()
                return;
            }

            // check new file
            if (newFile != null && newFile.exists()) {
                //                failReason = new MoveDownloadFileFailReason("the target file exist!", 
                // MoveDownloadFileFailReason
                //                        .TYPE_TARGET_FILE_EXIST);

                failReason = new OnMoveDownloadFileFailReason("the target file exist!", OnMoveDownloadFileFailReason
                        .TYPE_TARGET_FILE_EXIST);
                // goto finally,notifyFailed()
                return;
            }

            // create ParentFile of the newFile if it is not exists
            if (newFile != null && newFile.getParentFile() != null && !newFile.getParentFile().exists()) {
                FileUtil.createFileParentDir(newFile.getAbsolutePath());
            }

            // backup oldDirPath;
            String oldDirPath = downloadFileInfo.getFileDir();

            // move result
            boolean moveResult = false;

            try {
                mDownloadFileMover.moveDownloadFile(downloadFileInfo.getUrl(), mNewDirPath);
                moveResult = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!moveResult) {
                // move in db failed
                //                failReason = new MoveDownloadFileFailReason("update record error!", 
                // MoveDownloadFileFailReason
                //                        .TYPE_UPDATE_RECORD_ERROR);

                // move in db failed
                failReason = new OnMoveDownloadFileFailReason("update record error!", OnMoveDownloadFileFailReason
                        .TYPE_UPDATE_RECORD_ERROR);
                // goto finally,notifyFailed()
                return;
            }

            // move file in the file system
            moveResult = oldFile.renameTo(newFile);

            if (!moveResult) {
                // rollback in db
                try {
                    mDownloadFileMover.moveDownloadFile(downloadFileInfo.getUrl(), oldDirPath);
                } catch (Exception e) {
                    e.printStackTrace();
                    // try again
                    try {
                        mDownloadFileMover.moveDownloadFile(downloadFileInfo.getUrl(), oldDirPath);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                        // ignore   
                    }
                }

                //                failReason = new MoveDownloadFileFailReason("update record error!", 
                // MoveDownloadFileFailReason
                //                        .TYPE_UPDATE_RECORD_ERROR);

                failReason = new OnMoveDownloadFileFailReason("update record error!", OnMoveDownloadFileFailReason
                        .TYPE_UPDATE_RECORD_ERROR);
                // goto finally,notifyFailed()
                return;
            }

            // move success
        } catch (Exception e) {
            e.printStackTrace();

            //            failReason = new MoveDownloadFileFailReason(e);

            failReason = new OnMoveDownloadFileFailReason(e);
        } finally {
            // move succeed
            if (failReason == null) {
                // 2.move success
                notifySuccess(downloadFileInfo);

                Log.d(TAG, TAG + ".run.run 移动成功，url：" + mUrl);
            } else {
                // 2.move failed
                notifyFailed(downloadFileInfo, failReason);

                Log.d(TAG, TAG + ".run 删除失败，url：" + mUrl + ",failReason:" + failReason.getType());
            }

            Log.d(TAG, TAG + ".run 文件删除任务【已结束】，是否有异常：" + (failReason == null) + "，url：" + mUrl);
        }
    }

    /**
     * notifyPrepared
     */
    private void notifyPrepared(DownloadFileInfo downloadFileInfo) {
        if (mOnMoveDownloadFileListener == null) {
            return;
        }
        if (mIsSyncCallback) {
            mOnMoveDownloadFileListener.onMoveDownloadFilePrepared(downloadFileInfo);
        } else {
            OnMoveDownloadFileListener.MainThreadHelper.onMoveDownloadFilePrepared(downloadFileInfo, 
                    mOnMoveDownloadFileListener);
        }
    }

    /**
     * notifySuccess
     */
    private void notifySuccess(DownloadFileInfo downloadFileInfo) {
        if (mOnMoveDownloadFileListener == null) {
            return;
        }
        if (mIsSyncCallback) {
            mOnMoveDownloadFileListener.onMoveDownloadFileSuccess(downloadFileInfo);
        } else {
            OnMoveDownloadFileListener.MainThreadHelper.onMoveDownloadFileSuccess(downloadFileInfo, 
                    mOnMoveDownloadFileListener);
        }
    }

    /**
     * notifyFailed
     */
    private void notifyFailed(DownloadFileInfo downloadFileInfo, MoveDownloadFileFailReason failReason) {
        if (mOnMoveDownloadFileListener == null) {
            return;
        }
        if (mIsSyncCallback) {
            mOnMoveDownloadFileListener.onMoveDownloadFileFailed(downloadFileInfo, failReason);
        } else {
            OnMoveDownloadFileListener.MainThreadHelper.onMoveDownloadFileFailed(downloadFileInfo, failReason, 
                    mOnMoveDownloadFileListener);
        }
    }
}