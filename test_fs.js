const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");

const firebaseConfig = {
    apiKey: "AIzaSyCKB5BUPdMx7QYwoObJ-8ZExmW2JcguplQ",
    projectId: "stoveaide",
    storageBucket: "stoveaide.firebasestorage.app",
    databaseURL: "https://stoveaide-default-rtdb.asia-southeast1.firebasedatabase.app"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();

async function test() {
    console.log("Testing write to stoves/STOVE-PH-8842...");
    try {
        await db.collection("stoves").doc("STOVE-PH-8842").set({
            test: "hello",
            timestamp: Date.now()
        });
        console.log("SUCCESS!");
    } catch (err) {
        console.error("WRITE ERROR:", err.code, err.message);
    }
}

test();
