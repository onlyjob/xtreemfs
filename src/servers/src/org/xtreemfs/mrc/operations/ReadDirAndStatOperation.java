/*  Copyright (c) 2008 Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

 This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
 Grid Operating System, see <http://www.xtreemos.eu> for more details.
 The XtreemOS project has been developed with the financial support of the
 European Commission's IST program under contract #FP6-033576.

 XtreemFS is free software: you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free
 Software Foundation, either version 2 of the License, or (at your option)
 any later version.

 XtreemFS is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * AUTHORS: Jan Stender (ZIB)
 */

package org.xtreemfs.mrc.operations;

import java.util.Iterator;

import org.xtreemfs.foundation.logging.Logging;
import org.xtreemfs.interfaces.Constants;
import org.xtreemfs.interfaces.DirectoryEntry;
import org.xtreemfs.interfaces.DirectoryEntrySet;
import org.xtreemfs.interfaces.Stat;
import org.xtreemfs.interfaces.MRCInterface.readdirRequest;
import org.xtreemfs.interfaces.MRCInterface.readdirResponse;
import org.xtreemfs.mrc.MRCRequest;
import org.xtreemfs.mrc.MRCRequestDispatcher;
import org.xtreemfs.mrc.ac.FileAccessManager;
import org.xtreemfs.mrc.database.AtomicDBUpdate;
import org.xtreemfs.mrc.database.StorageManager;
import org.xtreemfs.mrc.database.VolumeInfo;
import org.xtreemfs.mrc.database.VolumeManager;
import org.xtreemfs.mrc.metadata.FileMetadata;
import org.xtreemfs.mrc.metadata.XLocList;
import org.xtreemfs.mrc.utils.MRCHelper;
import org.xtreemfs.mrc.utils.Path;
import org.xtreemfs.mrc.utils.PathResolver;

/**
 * 
 * @author stender
 */
public class ReadDirAndStatOperation extends MRCOperation {
    
    public ReadDirAndStatOperation(MRCRequestDispatcher master) {
        super(master);
    }
    
    @Override
    public void startRequest(MRCRequest rq) throws Throwable {
        
        final readdirRequest rqArgs = (readdirRequest) rq.getRequestArgs();
        
        final VolumeManager vMan = master.getVolumeManager();
        final FileAccessManager faMan = master.getFileAccessManager();
        
        validateContext(rq);
        
        Path p = new Path(rqArgs.getPath());
        
        StorageManager sMan = vMan.getStorageManagerByName(p.getComp(0));
        PathResolver res = new PathResolver(sMan, p);
        VolumeInfo volume = sMan.getVolumeInfo();
        
        // check whether the path prefix is searchable
        faMan.checkSearchPermission(sMan, res, rq.getDetails().userId, rq.getDetails().superUser, rq
                .getDetails().groupIds);
        
        // check whether file exists
        res.checkIfFileDoesNotExist();
        
        FileMetadata file = res.getFile();
        
        // check whether the directory grants read access
        faMan.checkPermission(FileAccessManager.O_RDONLY, sMan, file, res.getParentDirId(),
            rq.getDetails().userId, rq.getDetails().superUser, rq.getDetails().groupIds);
        
        AtomicDBUpdate update = sMan.createAtomicDBUpdate(master, rq);
        
        // if required, update POSIX timestamps
        if (!master.getConfig().isNoAtime())
            MRCHelper.updateFileTimes(res.getParentDirId(), file, true, false, false, sMan, update);
        
        DirectoryEntrySet dirContent = new DirectoryEntrySet();
        
        Iterator<FileMetadata> it = sMan.getChildren(res.getFile().getId());
        while (it.hasNext()) {
            
            FileMetadata child = it.next();
            
            if (child.getFileName().equals("")) {
                Logging.logMessage(Logging.LEVEL_WARN, this, "'%s' contains a %s with an empty name", p
                        .toString(), child.isDirectory() ? "directory" : "file");
                continue;
            }
            
            // // ignore the .fuse-hidden directory
            // if (res.getFile().getId() == 1 &&
            // child.getFileName().equals(".fuse-hidden"))
            // continue;
            //            
            String linkTarget = sMan.getSoftlinkTarget(child.getId());
            int mode = faMan
                    .getPosixAccessMode(sMan, child, rq.getDetails().userId, rq.getDetails().groupIds);
            mode |= linkTarget != null ? Constants.SYSTEM_V_FCNTL_H_S_IFLNK
                : child.isDirectory() ? Constants.SYSTEM_V_FCNTL_H_S_IFDIR
                    : Constants.SYSTEM_V_FCNTL_H_S_IFREG;
            long size = linkTarget != null ? linkTarget.length() : child.isDirectory() ? 0 : child.getSize();
            int blkSize = 0;
            if ((linkTarget == null) && (!file.isDirectory())) {
                XLocList xlocList = child.getXLocList();
                if ((xlocList != null) && (xlocList.getReplicaCount() > 0))
                    blkSize = xlocList.getReplica(0).getStripingPolicy().getStripeSize() * 1024;
            }
            Stat stat = new Stat(volume.getId().hashCode(), file.getId(), mode, child.getLinkCount(), 1, 1,
                0, size, blkSize, (long) child.getAtime() * (long) 1e9, (long) child.getMtime() * (long) 1e9,
                (long) child.getCtime() * (long) 1e9, volume.getId() + ":" + child.getId(), child
                        .getOwnerId(), child.getOwningGroupId(), linkTarget, child.getEpoch(), (int) child
                        .getW32Attrs());
            
            dirContent.add(new DirectoryEntry(child.getFileName(), stat));
        }
        
        // set the response
        rq.setResponse(new readdirResponse(dirContent));
        
        update.execute();
    }
    
}
