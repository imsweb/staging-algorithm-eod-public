package com.imsweb.staging.eod;

import java.io.IOException;

import com.imsweb.staging.eod.EodDataProvider.EodVersion;
import com.imsweb.staging.update.UpdaterUtils;

/**
 * Update the EOD data from the API
 */
public class EodUpdateFromAPI {

    private static final String _ALGORITHM = "eod";

    public static void main(String[] args) throws IOException {
        UpdaterUtils.update(_ALGORITHM, EodVersion.v1_7.getVersion());
    }

}
