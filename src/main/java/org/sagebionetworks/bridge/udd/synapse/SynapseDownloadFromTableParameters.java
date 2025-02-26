package org.sagebionetworks.bridge.udd.synapse;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.schema.UploadSchema;

/** Necessary args for downloading user data (query result CSV and attached file handles) from a Synapse table. */
public class SynapseDownloadFromTableParameters {
    private final String synapseTableId;
    private final String healthCode;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final File tempDir;
    private final UploadSchema schema;
    private final String studyId;

    /** Private constructor. To build, use builder. */
    private SynapseDownloadFromTableParameters(String synapseTableId, String healthCode, LocalDate startDate,
            LocalDate endDate, File tempDir, UploadSchema schema, String studyId) {
        this.synapseTableId = synapseTableId;
        this.healthCode = healthCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.tempDir = tempDir;
        this.schema = schema;
        this.studyId = studyId;
    }

    /** ID of the Synapse table to query against. */
    public String getSynapseTableId() {
        return synapseTableId;
    }

    /** User health code to filter against. */
    public String getHealthCode() {
        return healthCode;
    }

    /** Start date to filter against. */
    public LocalDate getStartDate() {
        return startDate;
    }

    /** End date to filter against. */
    public LocalDate getEndDate() {
        return endDate;
    }

    /** Temp dir to download CSV and file handles to. */
    public File getTempDir() {
        return tempDir;
    }

    /** Schema key, used to determine file names. */
    public UploadSchema getSchema() {
        return schema;
    }

    /** Study ID, used to get meta tables. */
    public String getStudyId() {
        return studyId;
    }

    /** Parameter class builder. */
    public static class Builder {
        private String synapseTableId;
        private String healthCode;
        private LocalDate startDate;
        private LocalDate endDate;
        private File tempDir;
        private UploadSchema schema;
        private String studyId;

        /** @see SynapseDownloadFromTableParameters#getSynapseTableId */
        public Builder withSynapseTableId(String synapseTableId) {
            this.synapseTableId = synapseTableId;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getHealthCode */
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getStartDate */
        public Builder withStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getEndDate */
        public Builder withEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getTempDir */
        public Builder withTempDir(File tempDir) {
            this.tempDir = tempDir;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getSchema */
        public Builder withSchema(UploadSchema schema) {
            this.schema = schema;
            return this;
        }

        /** @see SynapseDownloadFromTableParameters#getStudyId */
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }

        /** Builds the parameters object and validates parameters. */
        public SynapseDownloadFromTableParameters build() {
            if (StringUtils.isBlank(synapseTableId)) {
                throw new IllegalStateException("synapseTableId must be specified");
            }

            if (StringUtils.isBlank(healthCode)) {
                throw new IllegalStateException("healthCode must be specified");
            }

            if (startDate == null) {
                throw new IllegalStateException("startDate must be specified");
            }

            if (endDate == null) {
                throw new IllegalStateException("endDate must be specified");
            }

            if (startDate.isAfter(endDate)) {
                throw new IllegalStateException("startDate can't be after endDate");
            }

            if (tempDir == null) {
                throw new IllegalStateException("tempDir must be specified");
            }

            if (StringUtils.isBlank(studyId)) {
                throw new IllegalStateException("studyId must be specified");
            }

            return new SynapseDownloadFromTableParameters(synapseTableId, healthCode, startDate, endDate, tempDir,
                    schema, studyId);
        }
    }
}
