# Tests setups
| Files                                                                                   | Measure side | Array length | Number of clients | Pause between queries, ms | Number of queries |
|-----------------------------------------------------------------------------------------|--------------|--------------|-------------------|---------------------------|-------------------|
| CLIENT_ASYNC_LENGTH.csv, CLIENT_BLOCKING_LENGTH.csv, CLIENT_NON_BLOCKING_LENGTH.csv  | Client       | 500-10000, step 500        | 10      | 50                        | 10                |
| SERVER_ASYNC_LENGTH.csv, SERVER_BLOCKING_LENGTH.csv, SERVER_NON_BLOCKING_LENGTH.csv  | Server       | 500-10000, step 500        | 10      | 50                        | 10                |
| CLIENT_ASYNC_CLIENTS.csv, CLIENT_BLOCKING_CLIENTS.csv, CLIENT_NON_BLOCKING_CLIENTS.csv  | Client       | 5000         | 5-50, step 5      | 50                        | 10                |
| SERVER_ASYNC_CLIENTS.csv, SERVER_BLOCKING_CLIENTS.csv, SERVER_NON_BLOCKING_CLIENTS.csv  | Server       | 5000         | 5-50, step 5      | 50                        | 10                |
| CLIENT_ASYNC_PAUSE.csv, CLIENT_BLOCKING_PAUSE.csv, CLIENT_NON_BLOCKING_PAUSE.csv  | Client       | 5000        | 10      | 1-501, step 50                        | 10                |
| SERVER_ASYNC_PAUSE.csv, SERVER_BLOCKING_PAUSE.csv, SERVER_NON_BLOCKING_PAUSE.csv  | Server       | 5000        | 10      | 1-501, step 50                        | 10                |
