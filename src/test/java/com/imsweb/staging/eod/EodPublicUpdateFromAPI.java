package com.imsweb.staging.eod;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;

import com.imsweb.staging.eod.EodDataProvider.EodVersion;
import com.imsweb.staging.update.UpdaterUtils;

/**
 * Update the EOD data from the API
 */
public class EodPublicUpdateFromAPI {

    private static final String _ALGORITHM = "eod_public";

    public static void main(String[] args) throws IOException {
        UpdaterUtils.update(_ALGORITHM, EodVersion.v2_1.getVersion(), "c:/tmp/algorithms", new HashSet<>(Collections.singletonList("STAGING")));
    }

}
