package com.amazonaws.mobileconnectors.s3.transferutility;

import android.content.ContentValues;
import android.database.Cursor;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.logging.Log;
import com.amazonaws.logging.LogFactory;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferStatusUpdater;
import com.amazonaws.retry.RetryUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.util.Mimetypes;
import com.facebook.internal.ServerProtocol;
import com.facebook.stetho.server.http.HttpHeaders;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.query.SimpleComparison;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class UploadTask implements Callable<Boolean> {
    public static final Log g = LogFactory.a(UploadTask.class);
    public static final Map<String, CannedAccessControlList> h = new HashMap();

    /* renamed from: a, reason: collision with root package name */
    public final AmazonS3 f1789a;
    public final TransferRecord b;
    public final TransferDBUtil c;
    public final TransferStatusUpdater d;
    public Map<Integer, UploadPartTaskMetadata> e = new HashMap();
    public List<UploadPartRequest> f;

    /* loaded from: classes.dex */
    public class UploadPartTaskMetadata {

        /* renamed from: a, reason: collision with root package name */
        public Future<Boolean> f1790a;
        public long b;
        public TransferState c;

        public UploadPartTaskMetadata(UploadTask uploadTask) {
        }
    }

    /* loaded from: classes.dex */
    public class UploadTaskProgressListener implements ProgressListener {

        /* renamed from: a, reason: collision with root package name */
        public long f1791a;
        public final long b;

        public UploadTaskProgressListener(long j) {
            this.f1791a = j;
            this.b = j;
        }

        @Override // com.amazonaws.event.ProgressListener
        public void a(ProgressEvent progressEvent) {
        }
    }

    static {
        for (CannedAccessControlList cannedAccessControlList : CannedAccessControlList.values()) {
            h.put(cannedAccessControlList.toString(), cannedAccessControlList);
        }
    }

    public UploadTask(TransferRecord transferRecord, AmazonS3 amazonS3, TransferDBUtil transferDBUtil, TransferStatusUpdater transferStatusUpdater) {
        this.b = transferRecord;
        this.f1789a = amazonS3;
        this.c = transferDBUtil;
        this.d = transferStatusUpdater;
    }

    public final void a(int i, String str, String str2, String str3) throws AmazonClientException, AmazonServiceException {
        TransferDBUtil transferDBUtil = this.c;
        Objects.requireNonNull(transferDBUtil);
        ArrayList arrayList = new ArrayList();
        Cursor cursor = null;
        try {
            cursor = TransferDBUtil.d.b(transferDBUtil.d(i), null, null, null, null);
            while (cursor.moveToNext()) {
                arrayList.add(new PartETag(cursor.getInt(cursor.getColumnIndexOrThrow("part_num")), cursor.getString(cursor.getColumnIndexOrThrow("etag"))));
            }
            cursor.close();
            CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(str, str2, str3, arrayList);
            TransferUtility.a(completeMultipartUploadRequest);
            this.f1789a.d(completeMultipartUploadRequest);
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
            throw th;
        }
    }

    public final PutObjectRequest b(TransferRecord transferRecord) {
        File file = new File(transferRecord.m);
        PutObjectRequest putObjectRequest = new PutObjectRequest(transferRecord.k, transferRecord.l, file);
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.b.put(HttpHeaders.CONTENT_LENGTH, Long.valueOf(file.length()));
        String str = transferRecord.s;
        if (str != null) {
            objectMetadata.b.put("Cache-Control", str);
        }
        String str2 = transferRecord.q;
        if (str2 != null) {
            objectMetadata.b.put("Content-Disposition", str2);
        }
        String str3 = transferRecord.r;
        if (str3 != null) {
            objectMetadata.b.put("Content-Encoding", str3);
        }
        String str4 = transferRecord.p;
        if (str4 != null) {
            objectMetadata.m(str4);
        } else {
            objectMetadata.m(Mimetypes.a().b(file));
        }
        String str5 = transferRecord.t;
        if (str5 != null) {
            putObjectRequest.k = str5;
        }
        String str6 = transferRecord.v;
        if (str6 != null) {
            objectMetadata.e = str6;
        }
        if (transferRecord.w != null) {
            objectMetadata.c = new Date(Long.valueOf(transferRecord.w).longValue());
        }
        String str7 = transferRecord.x;
        if (str7 != null) {
            objectMetadata.a(str7);
        }
        Map<String, String> map = transferRecord.u;
        if (map != null) {
            objectMetadata.f1832a = map;
            String str8 = map.get("x-amz-tagging");
            if (str8 != null) {
                try {
                    String[] split = str8.split("&");
                    ArrayList arrayList = new ArrayList();
                    for (String str9 : split) {
                        String[] split2 = str9.split(SimpleComparison.EQUAL_TO_OPERATION);
                        arrayList.add(new Tag(split2[0], split2[1]));
                    }
                    putObjectRequest.n = new ObjectTagging(arrayList);
                } catch (Exception e) {
                    g.a("Error in passing the object tags as request headers.", e);
                }
            }
            String str10 = transferRecord.u.get("x-amz-website-redirect-location");
            if (str10 != null) {
                putObjectRequest.l = str10;
            }
            String str11 = transferRecord.u.get("x-amz-request-payer");
            if (str11 != null) {
                putObjectRequest.o = "requester".equals(str11);
            }
        }
        String str12 = transferRecord.z;
        if (str12 != null) {
            if (str12 == null) {
                objectMetadata.b.remove("Content-MD5");
            } else {
                objectMetadata.b.put("Content-MD5", str12);
            }
        }
        String str13 = transferRecord.y;
        if (str13 != null) {
            putObjectRequest.m = new SSEAwsKeyManagementParams(str13);
        }
        putObjectRequest.h = objectMetadata;
        String str14 = transferRecord.A;
        putObjectRequest.i = str14 == null ? null : (CannedAccessControlList) ((HashMap) h).get(str14);
        return putObjectRequest;
    }

    public final String c(PutObjectRequest putObjectRequest) {
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(putObjectRequest.d, putObjectRequest.e);
        initiateMultipartUploadRequest.g = putObjectRequest.i;
        initiateMultipartUploadRequest.f = putObjectRequest.h;
        initiateMultipartUploadRequest.h = putObjectRequest.m;
        initiateMultipartUploadRequest.i = putObjectRequest.n;
        TransferUtility.a(initiateMultipartUploadRequest);
        return this.f1789a.e(initiateMultipartUploadRequest).f1827a;
    }

    @Override // java.util.concurrent.Callable
    public Boolean call() throws Exception {
        long j;
        Cursor cursor;
        String str;
        Exception exc;
        Cursor cursor2;
        try {
            if (TransferNetworkLossHandler.a() != null && !TransferNetworkLossHandler.a().b()) {
                g.f("Network not connected. Setting the state to WAITING_FOR_NETWORK.");
                this.d.i(this.b.f1775a, TransferState.WAITING_FOR_NETWORK);
                return Boolean.FALSE;
            }
        } catch (TransferUtilityException e) {
            g.error("TransferUtilityException: [" + e + "]");
        }
        this.d.i(this.b.f1775a, TransferState.IN_PROGRESS);
        TransferRecord transferRecord = this.b;
        int i = transferRecord.c;
        if (i != 1 || transferRecord.e != 0) {
            if (i == 0) {
                PutObjectRequest b = b(transferRecord);
                ProgressListener c = this.d.c(this.b.f1775a);
                long length = b.f.length();
                TransferUtility.b(b);
                b.f1719a = c;
                try {
                    this.f1789a.c(b);
                    this.d.h(this.b.f1775a, length, length, true);
                    this.d.i(this.b.f1775a, TransferState.COMPLETED);
                    return Boolean.TRUE;
                } catch (Exception e2) {
                    if (TransferState.PENDING_CANCEL.equals(this.b.j)) {
                        TransferStatusUpdater transferStatusUpdater = this.d;
                        int i2 = this.b.f1775a;
                        TransferState transferState = TransferState.CANCELED;
                        transferStatusUpdater.i(i2, transferState);
                        g.f("Transfer is " + transferState);
                        return Boolean.FALSE;
                    }
                    if (TransferState.PENDING_PAUSE.equals(this.b.j)) {
                        TransferStatusUpdater transferStatusUpdater2 = this.d;
                        int i3 = this.b.f1775a;
                        TransferState transferState2 = TransferState.PAUSED;
                        transferStatusUpdater2.i(i3, transferState2);
                        g.f("Transfer is " + transferState2);
                        new ProgressEvent(0L).b = 32;
                        ((TransferStatusUpdater.TransferProgressListener) c).a(new ProgressEvent(0L));
                        return Boolean.FALSE;
                    }
                    try {
                        if (TransferNetworkLossHandler.a() != null && !TransferNetworkLossHandler.a().b()) {
                            Log log = g;
                            log.f("Thread:[" + Thread.currentThread().getId() + "]: Network wasn't available.");
                            this.d.i(this.b.f1775a, TransferState.WAITING_FOR_NETWORK);
                            log.e("Network Connection Interrupted: Moving the TransferState to WAITING_FOR_NETWORK");
                            new ProgressEvent(0L).b = 32;
                            ((TransferStatusUpdater.TransferProgressListener) c).a(new ProgressEvent(0L));
                            return Boolean.FALSE;
                        }
                    } catch (TransferUtilityException e3) {
                        g.error("TransferUtilityException: [" + e3 + "]");
                    }
                    if (RetryUtils.b(e2)) {
                        g.f("Transfer is interrupted. " + e2);
                        this.d.i(this.b.f1775a, TransferState.FAILED);
                        return Boolean.FALSE;
                    }
                    Log log2 = g;
                    StringBuilder a2 = defpackage.a.a("Failed to upload: ");
                    a2.append(this.b.f1775a);
                    a2.append(" due to ");
                    a2.append(e2.getMessage());
                    log2.e(a2.toString());
                    this.d.f(this.b.f1775a, e2);
                    this.d.i(this.b.f1775a, TransferState.FAILED);
                    return Boolean.FALSE;
                }
            }
            return Boolean.FALSE;
        }
        String str2 = transferRecord.n;
        if (str2 != null && !str2.isEmpty()) {
            TransferDBUtil transferDBUtil = this.c;
            int i4 = this.b.f1775a;
            Objects.requireNonNull(transferDBUtil);
            try {
                cursor2 = TransferDBUtil.d.b(transferDBUtil.d(i4), null, null, null, null);
                j = 0;
                while (cursor2.moveToNext()) {
                    try {
                        if (TransferState.PART_COMPLETED.equals(TransferState.getState(cursor2.getString(cursor2.getColumnIndexOrThrow(ServerProtocol.DIALOG_PARAM_STATE))))) {
                            j += cursor2.getLong(cursor2.getColumnIndexOrThrow("bytes_total"));
                        }
                    } catch (Throwable th) {
                        th = th;
                        if (cursor2 != null) {
                            cursor2.close();
                        }
                        throw th;
                    }
                }
                cursor2.close();
                if (j > 0) {
                    g.f(String.format("Resume transfer %d from %d bytes", Integer.valueOf(this.b.f1775a), Long.valueOf(j)));
                }
            } catch (Throwable th2) {
                th = th2;
                cursor2 = null;
            }
        } else {
            PutObjectRequest b2 = b(this.b);
            TransferUtility.a(b2);
            try {
                this.b.n = c(b2);
                TransferDBUtil transferDBUtil2 = this.c;
                TransferRecord transferRecord2 = this.b;
                int i5 = transferRecord2.f1775a;
                String str3 = transferRecord2.n;
                Objects.requireNonNull(transferDBUtil2);
                ContentValues contentValues = new ContentValues();
                contentValues.put("multipart_id", str3);
                TransferDBUtil.d.c(transferDBUtil2.e(i5), contentValues, null, null);
                j = 0;
            } catch (AmazonClientException e4) {
                Log log3 = g;
                StringBuilder a3 = defpackage.a.a("Error initiating multipart upload: ");
                a3.append(this.b.f1775a);
                a3.append(" due to ");
                a3.append(e4.getMessage());
                log3.a(a3.toString(), e4);
                this.d.f(this.b.f1775a, e4);
                this.d.i(this.b.f1775a, TransferState.FAILED);
                return Boolean.FALSE;
            }
        }
        UploadTaskProgressListener uploadTaskProgressListener = new UploadTaskProgressListener(j);
        TransferStatusUpdater transferStatusUpdater3 = this.d;
        TransferRecord transferRecord3 = this.b;
        transferStatusUpdater3.h(transferRecord3.f1775a, j, transferRecord3.f, false);
        TransferDBUtil transferDBUtil3 = this.c;
        TransferRecord transferRecord4 = this.b;
        int i6 = transferRecord4.f1775a;
        String str4 = transferRecord4.n;
        Objects.requireNonNull(transferDBUtil3);
        ArrayList arrayList = new ArrayList();
        try {
            cursor = TransferDBUtil.d.b(transferDBUtil3.d(i6), null, null, null, null);
            while (cursor.moveToNext()) {
                try {
                    if (!TransferState.PART_COMPLETED.equals(TransferState.getState(cursor.getString(cursor.getColumnIndexOrThrow(ServerProtocol.DIALOG_PARAM_STATE))))) {
                        UploadPartRequest uploadPartRequest = new UploadPartRequest();
                        uploadPartRequest.d = cursor.getInt(cursor.getColumnIndexOrThrow(FieldType.FOREIGN_ID_FIELD_SUFFIX));
                        cursor.getInt(cursor.getColumnIndexOrThrow("main_upload_id"));
                        uploadPartRequest.e = cursor.getString(cursor.getColumnIndexOrThrow("bucket_name"));
                        uploadPartRequest.f = cursor.getString(cursor.getColumnIndexOrThrow("key"));
                        uploadPartRequest.g = str4;
                        uploadPartRequest.j = new File(cursor.getString(cursor.getColumnIndexOrThrow("file")));
                        uploadPartRequest.k = cursor.getLong(cursor.getColumnIndexOrThrow("file_offset"));
                        uploadPartRequest.h = cursor.getInt(cursor.getColumnIndexOrThrow("part_num"));
                        uploadPartRequest.i = cursor.getLong(cursor.getColumnIndexOrThrow("bytes_total"));
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_last_part"));
                        arrayList.add(uploadPartRequest);
                    }
                } catch (Throwable th3) {
                    th = th3;
                    if (cursor != null) {
                        cursor.close();
                    }
                    throw th;
                }
            }
            cursor.close();
            this.f = arrayList;
            Log log4 = g;
            StringBuilder a4 = defpackage.a.a("Multipart upload ");
            a4.append(this.b.f1775a);
            a4.append(" in ");
            a4.append(((ArrayList) this.f).size());
            a4.append(" parts.");
            log4.f(a4.toString());
            Iterator it = ((ArrayList) this.f).iterator();
            while (it.hasNext()) {
                UploadPartRequest uploadPartRequest2 = (UploadPartRequest) it.next();
                TransferUtility.a(uploadPartRequest2);
                UploadPartTaskMetadata uploadPartTaskMetadata = new UploadPartTaskMetadata(this);
                uploadPartTaskMetadata.b = 0L;
                uploadPartTaskMetadata.c = TransferState.WAITING;
                ((HashMap) this.e).put(Integer.valueOf(uploadPartRequest2.h), uploadPartTaskMetadata);
                uploadPartTaskMetadata.f1790a = TransferThreadPool.c(new UploadPartTask(uploadPartTaskMetadata, uploadTaskProgressListener, uploadPartRequest2, this.f1789a, this.c));
            }
            try {
                Iterator it2 = ((HashMap) this.e).values().iterator();
                boolean z = true;
                while (it2.hasNext()) {
                    try {
                        z &= ((UploadPartTaskMetadata) it2.next()).f1790a.get().booleanValue();
                    } catch (Exception e5) {
                        exc = e5;
                        str = " due to ";
                        g.error("Upload resulted in an exception. " + exc);
                        Iterator it3 = ((HashMap) this.e).values().iterator();
                        while (it3.hasNext()) {
                            ((UploadPartTaskMetadata) it3.next()).f1790a.cancel(true);
                        }
                        if (TransferState.PENDING_CANCEL.equals(this.b.j)) {
                            TransferStatusUpdater transferStatusUpdater4 = this.d;
                            int i7 = this.b.f1775a;
                            TransferState transferState3 = TransferState.CANCELED;
                            transferStatusUpdater4.i(i7, transferState3);
                            g.f("Transfer is " + transferState3);
                            return Boolean.FALSE;
                        }
                        if (TransferState.PENDING_PAUSE.equals(this.b.j)) {
                            TransferStatusUpdater transferStatusUpdater5 = this.d;
                            int i8 = this.b.f1775a;
                            TransferState transferState4 = TransferState.PAUSED;
                            transferStatusUpdater5.i(i8, transferState4);
                            g.f("Transfer is " + transferState4);
                            return Boolean.FALSE;
                        }
                        for (UploadPartTaskMetadata uploadPartTaskMetadata2 : ((HashMap) this.e).values()) {
                            TransferState transferState5 = TransferState.WAITING_FOR_NETWORK;
                            if (transferState5.equals(uploadPartTaskMetadata2.c)) {
                                g.f("Individual part is WAITING_FOR_NETWORK.");
                                this.d.i(this.b.f1775a, transferState5);
                                return Boolean.FALSE;
                            }
                        }
                        try {
                            if (TransferNetworkLossHandler.a() != null && !TransferNetworkLossHandler.a().b()) {
                                g.f("Network not connected. Setting the state to WAITING_FOR_NETWORK.");
                                this.d.i(this.b.f1775a, TransferState.WAITING_FOR_NETWORK);
                                return Boolean.FALSE;
                            }
                        } catch (TransferUtilityException e6) {
                            g.error("TransferUtilityException: [" + e6 + "]");
                        }
                        if (RetryUtils.b(exc)) {
                            g.f("Transfer is interrupted. " + exc);
                            this.d.i(this.b.f1775a, TransferState.FAILED);
                            return Boolean.FALSE;
                        }
                        Log log5 = g;
                        StringBuilder a5 = defpackage.a.a("Error encountered during multi-part upload: ");
                        a5.append(this.b.f1775a);
                        a5.append(str);
                        a5.append(exc.getMessage());
                        log5.a(a5.toString(), exc);
                        this.d.f(this.b.f1775a, exc);
                        this.d.i(this.b.f1775a, TransferState.FAILED);
                        return Boolean.FALSE;
                    }
                }
                if (!z) {
                    try {
                        if (TransferNetworkLossHandler.a() != null && !TransferNetworkLossHandler.a().b()) {
                            g.f("Network not connected. Setting the state to WAITING_FOR_NETWORK.");
                            this.d.i(this.b.f1775a, TransferState.WAITING_FOR_NETWORK);
                            return Boolean.FALSE;
                        }
                    } catch (TransferUtilityException e7) {
                        g.error("TransferUtilityException: [" + e7 + "]");
                    }
                }
                Log log6 = g;
                StringBuilder a6 = defpackage.a.a("Completing the multi-part upload transfer for ");
                a6.append(this.b.f1775a);
                log6.f(a6.toString());
                try {
                    TransferRecord transferRecord5 = this.b;
                    a(transferRecord5.f1775a, transferRecord5.k, transferRecord5.l, transferRecord5.n);
                    TransferStatusUpdater transferStatusUpdater6 = this.d;
                    TransferRecord transferRecord6 = this.b;
                    int i9 = transferRecord6.f1775a;
                    long j2 = transferRecord6.f;
                    transferStatusUpdater6.h(i9, j2, j2, true);
                    this.d.i(this.b.f1775a, TransferState.COMPLETED);
                    return Boolean.TRUE;
                } catch (AmazonClientException e8) {
                    Log log7 = g;
                    StringBuilder a7 = defpackage.a.a("Failed to complete multipart: ");
                    a7.append(this.b.f1775a);
                    a7.append(" due to ");
                    a7.append(e8.getMessage());
                    log7.a(a7.toString(), e8);
                    TransferRecord transferRecord7 = this.b;
                    int i10 = transferRecord7.f1775a;
                    String str5 = transferRecord7.k;
                    String str6 = transferRecord7.l;
                    String str7 = transferRecord7.n;
                    log7.f("Aborting the multipart since complete multipart failed.");
                    try {
                        this.f1789a.f(new AbortMultipartUploadRequest(str5, str6, str7));
                        log7.e("Successfully aborted multipart upload: " + i10);
                    } catch (AmazonClientException e9) {
                        g.b("Failed to abort the multipart upload: " + i10, e9);
                    }
                    this.d.f(this.b.f1775a, e8);
                    this.d.i(this.b.f1775a, TransferState.FAILED);
                    return Boolean.FALSE;
                }
            } catch (Exception e10) {
                str = " due to ";
                exc = e10;
            }
        } catch (Throwable th4) {
            th = th4;
            cursor = null;
        }
    }
}
