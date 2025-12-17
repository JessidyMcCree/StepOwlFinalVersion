//Import modules
const express = require("express")
const bodyParser = require("body-parser")
const session = require("express-session")
const connection = require("./database")

//Initialize express
const app = express()

//Apply body parser to the incoming requests
app.use(bodyParser.urlencoded({extended: false}))

// Parses the body of the request as JSON

app.use(express.json())



app.post("/addToInventory/add", (req, res) => {
    const { playerId, items } = req.body;
    if (!playerId || !items) return res.status(400).json({ error: "Missing playerId or items" });

    connection.query("DELETE FROM inventory WHERE playerId = ?", [playerId], (err) => {
        if (err) {
            console.error("Erro ao limpar inventário:", err);
            return res.status(500).json({ error: err.message });
        }

        items.forEach(item => {
            connection.query(
                "INSERT INTO inventory (playerId, itemId, quantity) VALUES (?, ?, ?)",
                [playerId, item.itemId, item.quantity],
                (err, result) => {
                    if (err) console.error("Erro no INSERT:", err);
                }
            );
        });

        res.json({ success: true });
    });
});

app.get("/inventory/:playerId", (req, res) => {
    const playerId = req.params.playerId;
    console.log("GET /inventory para playerId:", playerId);

    connection.query(
        "SELECT itemId, quantity FROM inventory WHERE playerId = ?",
        [playerId],
        (err, results) => {
            if (err) {
                console.error("Erro na query:", err);
                return res.status(500).json({ error: err.message });
            }
            console.log("Resultados obtidos:", results);
            res.json(results);
        }
    );
});

app.listen(4000, () => console.log("🙌 Server running on port 4000"));