const mysql = require("mysql2");

const pool = mysql.createPool({
    host:  "ia8ezm.h.filess.io",
    user:  "StepOwl_worryedge",
    password:  "8b372de93641d9dbf4d33ec7fe66f7d3249e790c",
    port:  61002,
    database:  "StepOwl_worryedge",
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
});

module.exports = pool;
